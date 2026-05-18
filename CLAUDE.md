# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

Teaching demo for nine Redis patterns layered on a Spring Boot 3.5 / Java 17 e-commerce service. Products are persisted in MongoDB; Redis carries every cache and fast-path use case. Stack is intentionally aligned to T-Mobile's T-Life InfoBot Java services (Spring Boot 3.5.6, Java 17, MongoDB, Lettuce). The mapping back to InfoBot is documented in `README.md` and worth reading before suggesting changes.

## Common commands

```powershell
# Start MongoDB + Redis (required before running the app)
docker compose up -d

# Run the app (no Maven wrapper — install Maven, or run `mvn -N wrapper:wrapper` once)
mvn spring-boot:run

# Build
mvn clean package

# Run tests (currently only the default Spring context test exists)
mvn test
mvn -Dtest=EcomApplicationTests test     # single test

# Watch Redis traffic live
docker exec -it ecom-redis redis-cli MONITOR

# Wipe Redis between experiments (clears caches, leaderboards, locks)
docker exec -it ecom-redis redis-cli FLUSHDB
```

App listens on `:8080`; health is `GET /actuator/health`. `DataSeeder` inserts 5 sample products on first boot.

## Architecture: where each Redis pattern lives

Every service class implements exactly one pattern and is named after it. `OrderService.placeOrder` is the only place that composes patterns (idempotency + lock + leaderboard + pub/sub) — it is the canonical "realistic" flow and the right reference when adding new composite workflows.

| Pattern | Class | Redis primitive |
|---|---|---|
| Cache-aside | `ProductService` | `@Cacheable` / `@CacheEvict` via `RedisCacheManager` |
| Hash (cart) | `CartService` | `HSET` / `HGETALL` on `cart:{userId}` |
| List (recent views) | `RecentlyViewedService` | `LPUSH` + `LTRIM` on `recent:{userId}` |
| Set + intersection | `TagService` | `SADD` / `SINTER` on `tag:{name}` |
| Sorted set | `LeaderboardService` | `ZINCRBY` / `ZREVRANGE` on `leaderboard:bestsellers` |
| Rate limit | `RateLimiterService` | `INCR` + `EXPIRE` |
| Distributed lock | `DistributedLockService` | `SET NX EX` + Lua-guarded release |
| Idempotency | `IdempotencyService` | `SET NX EX` keyed on `idem:{key}` |
| Pub/Sub | `EventPublisher` → `OrderEventListener` | `PUBLISH` / `SUBSCRIBE` |

## Wiring you must understand before changing Redis config

`config/RedisConfig.java` is the single source of truth for Redis beans:

- **`RedisTemplate<String,Object>`** — String keys, JSON values (`GenericJackson2JsonRedisSerializer`). Used by every manual-pattern service (Hash/List/Set/ZSet/lock/idempotency). Values are human-readable in `redis-cli` and tagged with `@class` for polymorphic deserialization.
- **`StringRedisTemplate`** — used by `DistributedLockService` (Lua script expects raw strings) and rate limiting.
- **`RedisCacheManager`** — backs all `@Cacheable`. Per-cache TTLs are declared in this file, not on the annotation: `products` 30m, `categories` 2h, `userPrefs` 5m, default 10m. Add a new cache name → add its TTL here.
- **`RedisMessageListenerContainer`** — pub/sub fan-out. `OrderEventListener.subscribe()` registers channels via `@PostConstruct`.

Renaming or moving a domain class invalidates cached JSON (the `@class` marker no longer resolves). Flush Redis after such changes — this bites during refactors.

## Behavior worth knowing

- `OrderService.placeOrder` double-checks idempotency **inside** the lock to close the TOCTOU window between the outer fast-path check and lock acquisition. Preserve this pattern when adding similar flows.
- `DistributedLockService.release` is a Lua CAS-style delete (only deletes if the token still matches). Never replace it with a plain `DEL` — that reintroduces the "delete someone else's lock" bug called out in the class javadoc.
- `ProductService` uses `unless = "#result == null"` to skip negative caching. That's deliberate; do not "fix" it without considering cache-penetration tradeoffs documented in `README.md` § Anti-patterns.
- Recently-viewed tracking expects `X-User-Id` header on `GET /products/{id}`. Missing header = no list write, by design.

## Configuration

`src/main/resources/application.yml` — connection settings only. Both Mongo URI and Redis host are env-overridable (`MONGO_URI`, `REDIS_HOST`, `REDIS_PORT`). `logging.level.com.learn.ecom=DEBUG` is on so the `[CACHE MISS]`, `[PUBLISH]`, `[SUBSCRIBE]`, `[IDEMPOTENT]` log markers are visible — the README's curl walkthroughs rely on seeing these.

`docker-compose.yml` exposes Redis on the standard `6379` and Mongo on `27017` with named volumes (`mongo-data`, `redis-data`). `docker compose down -v` wipes both.
