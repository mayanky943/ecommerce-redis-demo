package com.learn.ecom.service;

import com.learn.ecom.domain.CartItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pattern 2: HASH structure (HSET / HGET / HDEL / HGETALL).
 *
 * One Redis key per user, with productId as the hash field and quantity as the value:
 *
 *   key   = cart:{userId}
 *   field = productId
 *   value = quantity (as string)
 *
 * Why a Hash and not a JSON blob? Because you can update a single line
 * (HINCRBY cart:u-1 p-3 1) without round-tripping the whole cart through
 * the network. Atomic, smaller payloads, easier to reason about.
 *
 * TTL set on every write so abandoned carts auto-clean after 24h.
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private static final Duration CART_TTL = Duration.ofHours(24);
    private final StringRedisTemplate redis;

    private String key(String userId) { return "cart:" + userId; }

    public void addItem(String userId, String productId, int quantity) {
        HashOperations<String, Object, Object> h = redis.opsForHash();
        h.increment(key(userId), productId, quantity);     // HINCRBY — atomic
        redis.expire(key(userId), CART_TTL);
    }

    public void removeItem(String userId, String productId) {
        redis.opsForHash().delete(key(userId), productId);
    }

    public List<CartItem> getCart(String userId) {
        Map<Object, Object> entries = redis.opsForHash().entries(key(userId));
        List<CartItem> items = new java.util.ArrayList<>();
        entries.forEach((pid, qty) ->
            items.add(new CartItem(pid.toString(), Integer.parseInt(qty.toString())))
        );
        return items;
    }

    public Map<String, Integer> getCartMap(String userId) {
        Map<Object, Object> entries = redis.opsForHash().entries(key(userId));
        Map<String, Integer> out = new HashMap<>();
        entries.forEach((pid, qty) -> out.put(pid.toString(), Integer.parseInt(qty.toString())));
        return out;
    }

    public void clear(String userId) {
        redis.delete(key(userId));
    }
}
