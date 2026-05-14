# ecommerce-redis-demo

A small Spring Boot 3.5 / Java 17 service that demonstrates **every Redis
pattern** from the `01-Redis-with-Spring-Boot.md` README using a simple
e-commerce domain — products in MongoDB, Redis for every cache / fast-path
use case.

Stack matches T-Life InfoBot's Java services: **Spring Boot 3.5.6, Java 17,
spring-cloud-style config, MongoDB, Redis (Lettuce client).**

---

## Quick start

You need **Java 17+**, **Maven 3.9+**, and **Docker** (for MongoDB + Redis).

```bash
# 1) start MongoDB + Redis
cd ecommerce-redis-demo
docker compose up -d

# 2) run the app (Maven not bundled — install Maven once, or generate the
#    wrapper with `mvn -N wrapper:wrapper` and use ./mvnw afterwards)
mvn spring-boot:run

# 3) verify it's up
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

Sample products are seeded automatically on first run (`DataSeeder`).
Stop everything with `docker compose down` (add `-v` to wipe data).

To watch Redis as the app talks to it, open a second terminal:

```bash
docker exec -it ecom-redis redis-cli MONITOR
```

---

## What pattern lives where

| # | Redis pattern        | Service class           | Endpoint to exercise it |
|---|----------------------|-------------------------|-------------------------|
| 1 | **Cache-aside**      | `ProductService`        | `GET /products/{id}` |
| 2 | **Hash**             | `CartService`           | `POST /carts/{userId}/items` |
| 3 | **List**             | `RecentlyViewedService` | `GET /carts/{userId}/recent` |
| 4 | **Set**              | `TagService`            | `GET /products/by-tags?tags=apple,wireless` |
| 5 | **Sorted Set**       | `LeaderboardService`    | `GET /leaderboard?n=5` |
| 6 | **Rate limit**       | `RateLimiterService`    | `GET /demo/rate-limited` |
| 7 | **Distributed lock** | `DistributedLockService`| `POST /demo/lock/{key}` |
| 8 | **Idempotency key**  | `IdempotencyService`    | `POST /orders` (`Idempotency-Key` header) |
| 9 | **Pub/Sub**          | `EventPublisher` + `OrderEventListener` | implicit on `POST /orders` |

`OrderService.placeOrder` ties **lock + idempotency + leaderboard + pub/sub**
together — the most realistic example in the project.

---

## Walkthrough (curl recipes)

### Pattern 1 — Cache-aside (`@Cacheable`)

```bash
# First call: MongoDB read, look at logs for [CACHE MISS]
curl -s http://localhost:8080/products/p-1 | jq

# Second call: Redis hit, no MongoDB log line this time
curl -s http://localhost:8080/products/p-1 | jq

# Inspect what's actually stored
docker exec -it ecom-redis redis-cli GET products::p-1
docker exec -it ecom-redis redis-cli TTL products::p-1

# Mutate -> @CacheEvict fires on save -> next GET refills the cache
curl -s -X PUT http://localhost:8080/products/p-1 \
  -H 'Content-Type: application/json' \
  -d '{"name":"iPhone 17 Pro","price":1199,"stock":50,"category":"phones"}'
```

### Pattern 2 — Hash for shopping cart

```bash
curl -X POST http://localhost:8080/carts/u-1/items \
  -H 'Content-Type: application/json' -d '{"productId":"p-1","quantity":2}'

curl -X POST http://localhost:8080/carts/u-1/items \
  -H 'Content-Type: application/json' -d '{"productId":"p-4","quantity":1}'

curl http://localhost:8080/carts/u-1 | jq

# In Redis: HGETALL cart:u-1
docker exec -it ecom-redis redis-cli HGETALL cart:u-1
```

### Pattern 3 — List of recently viewed

```bash
curl -H 'X-User-Id: u-1' http://localhost:8080/products/p-1
curl -H 'X-User-Id: u-1' http://localhost:8080/products/p-2
curl -H 'X-User-Id: u-1' http://localhost:8080/products/p-3

curl http://localhost:8080/carts/u-1/recent | jq
# ["p-3","p-2","p-1"]   ← newest first, trimmed to max 10

docker exec -it ecom-redis redis-cli LRANGE recent:u-1 0 -1
```

### Pattern 4 — Set + intersection for tag search

```bash
curl -X POST http://localhost:8080/products/p-1/tags/apple
curl -X POST http://localhost:8080/products/p-4/tags/apple
curl -X POST http://localhost:8080/products/p-4/tags/wireless
curl -X POST http://localhost:8080/products/p-5/tags/apple
curl -X POST http://localhost:8080/products/p-5/tags/wireless

# Apple AND wireless: should be {p-4, p-5}
curl 'http://localhost:8080/products/by-tags?tags=apple,wireless' | jq

docker exec -it ecom-redis redis-cli SMEMBERS tag:apple
docker exec -it ecom-redis redis-cli SINTER tag:apple tag:wireless
```

### Pattern 5 — Sorted set leaderboard

```bash
# Record sales directly
curl -X POST 'http://localhost:8080/leaderboard/p-1?units=3'
curl -X POST 'http://localhost:8080/leaderboard/p-2?units=5'
curl -X POST 'http://localhost:8080/leaderboard/p-3?units=1'

curl 'http://localhost:8080/leaderboard?n=5' | jq
# [{ "productId":"p-2","units":5 }, { "productId":"p-1","units":3 }, ...]

docker exec -it ecom-redis redis-cli ZREVRANGE leaderboard:bestsellers 0 -1 WITHSCORES
```

### Pattern 6 — Rate limit (5/min on `default` bucket)

```bash
for i in $(seq 1 7); do
  curl -s -o /dev/null -w "%{http_code}\n" \
    'http://localhost:8080/demo/rate-limited?bucket=default&perMinute=5'
done
# 200 200 200 200 200 429 429
```

### Pattern 7 — Distributed lock

```bash
# First caller acquires it for 30 seconds
T=$(curl -s -X POST 'http://localhost:8080/demo/lock/inventory-sync?ttlSeconds=30')
echo "$T"
# acquired token=<uuid>

# Within 30 s, a second caller is rejected
curl -i -X POST 'http://localhost:8080/demo/lock/inventory-sync?ttlSeconds=30'
# HTTP/1.1 409 Conflict

# Release using the same token (extract UUID from "$T")
TOKEN=${T##*=}
curl -X DELETE "http://localhost:8080/demo/lock/inventory-sync?token=$TOKEN"
```

### Pattern 8 + 9 — Idempotency + lock + pub/sub (`POST /orders`)

```bash
# Add items to a cart first
curl -X POST http://localhost:8080/carts/u-1/items \
  -H 'Content-Type: application/json' -d '{"productId":"p-1","quantity":1}'
curl -X POST http://localhost:8080/carts/u-1/items \
  -H 'Content-Type: application/json' -d '{"productId":"p-4","quantity":2}'

# First submit — creates the order, publishes order.placed (see logs)
KEY=$(uuidgen)
curl -s -X POST "http://localhost:8080/orders?userId=u-1" \
  -H "Idempotency-Key: $KEY" | jq

# Replay with the SAME key — returns the same order id, no duplicate in Mongo
curl -s -X POST "http://localhost:8080/orders?userId=u-1" \
  -H "Idempotency-Key: $KEY" | jq

# Look at the app logs — you'll see [PUBLISH] order.placed then [SUBSCRIBE] received
docker exec -it ecom-redis redis-cli GET "idem:$KEY"
```

---

## Code layout

```
src/main/java/com/learn/ecom/
├── EcomApplication.java          @SpringBootApplication, @EnableCaching
├── config/
│   └── RedisConfig.java          RedisTemplate, CacheManager (per-cache TTL), pub/sub container
├── domain/
│   ├── Product.java              @Document("products")
│   ├── CartItem.java             POJO, lives in Redis only
│   └── Order.java                @Document("orders") with @Indexed unique idempotencyKey
├── repository/
│   ├── ProductRepository.java    MongoRepository
│   └── OrderRepository.java      MongoRepository + findByIdempotencyKey
├── seed/
│   └── DataSeeder.java           CommandLineRunner — seeds 5 products on first boot
├── service/
│   ├── ProductService.java       Pattern 1: @Cacheable / @CacheEvict
│   ├── CartService.java          Pattern 2: Hash
│   ├── RecentlyViewedService.java Pattern 3: List (LPUSH + LTRIM)
│   ├── TagService.java           Pattern 4: Set + SINTER
│   ├── LeaderboardService.java   Pattern 5: Sorted Set (ZINCRBY / ZREVRANGE)
│   ├── RateLimiterService.java   Pattern 6: INCR + EXPIRE
│   ├── DistributedLockService.java Pattern 7: SETNX + Lua release
│   ├── IdempotencyService.java   Pattern 8: SETNX with TTL
│   ├── EventPublisher.java       Pattern 9a: PUBLISH
│   └── OrderService.java         combines lock + idempotency + leaderboard + pub/sub
├── pubsub/
│   └── OrderEventListener.java   Pattern 9b: SUBSCRIBE (MessageListener)
└── controller/
    ├── ProductController.java
    ├── CartController.java
    ├── OrderController.java
    ├── LeaderboardController.java
    └── DemoController.java       rate-limit + lock playgrounds
```

---

## Anti-patterns and pitfalls you should be able to discuss

The code is intentionally simple, but a real production version would also need:

- **TTL jitter** on cache writes to avoid cache-avalanche (every key expiring
  on the minute boundary at the same moment).
- **Negative caching** for the `null` case in `findById` if you frequently get
  requests for IDs that don't exist (cache penetration). Right now we
  `unless = "#result == null"` which avoids storing nulls — that's correct for
  low-volume bad-id traffic but the wrong call for adversarial traffic.
- **Connection-failure degradation** — if Redis is unreachable, `@Cacheable`
  by default throws and the request fails. Consider `RedisCacheConfiguration`
  with a `CacheErrorHandler` that logs and falls through to the DB.
- **Big-key avoidance** — don't put a 5 MB JSON blob in a single Redis key.
  Split it (hash with per-field), or stream it elsewhere.
- **Cluster mode** — multi-key ops (transactions, `SINTER`, Lua) require keys
  to share a hash slot. Use hash tags like `{user:42}:cart` so related keys
  always co-locate.
- **Redlock vs single-node lock** — the lock in this demo is single-node-safe
  but loses the lock during a Redis master failover. For correctness across
  failover use Redlock with 5 independent nodes.

---

## Mapping back to T-Life InfoBot

| Pattern here | Where the InfoBot stack would put it |
|---|---|
| `@Cacheable` product cache | Could be added to `auth-manager` for token revalidation, or `bot-manager` for `GET_SUGGESTED_ROUTINES` responses. Today: not used in the Java path; Caffeine in `state-manager` plays a similar role. |
| Cart Hash | Conversation state per user. Today: lives in MongoDB (via `state-manager`) with a Caffeine read-through. |
| Rate limit | Protect `/intelligence/v1/chat` from per-msisdn abuse and OpenAI quota burn. |
| Distributed lock | Scheduled jobs in `state-manager` that should run on exactly one pod (e.g. close-stale-conversations). |
| Idempotency | The `CONVERSATION_TO_BE_CLOSED` pod and other write endpoints — protect against client retry duplicating state. |
| Sorted set | Suggested-topics ranking, A/B test winner buckets. |
| Pub/Sub | Already happens through Kafka in InfoBot; Redis pub/sub would only fit for ephemeral cross-pod nudges. |
| TTS audio cache | **Real usage** in `611-core` (Python) — Redis stores `BAN+language → audio bytes`. |

---

## Troubleshooting

- **`UnknownHostException: localhost`** — make sure `docker compose up -d` finished and `redis-cli PING` returns PONG.
- **`Could not connect to MongoDB`** — `docker compose ps` should show `ecom-mongo` healthy. Try `docker logs ecom-mongo`.
- **Cache key doesn't appear in Redis** — Spring `@Cacheable` only stores when the method returns a non-null. Check the log; if you see `[CACHE MISS]` twice in a row for the same id, something is wrong with serialization. Verify Lombok is generating the `Product` no-args constructor.
- **`@class` deserialization errors** — the JSON serializer stores a `@class` marker. If you change a class's package or name, old cached entries can't deserialize. Hit `DELETE /products/cache` or just `FLUSHDB` in redis-cli during dev.
