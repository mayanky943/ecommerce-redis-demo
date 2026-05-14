package com.learn.ecom.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Pattern 3: LIST — keep "last N viewed products" per user.
 *
 *   key = recent:{userId}
 *   LPUSH new id → LTRIM 0 N-1 → list always holds at most N entries, newest first.
 *
 * Two operations, both O(1) / O(log N). No DB needed for this kind of view.
 */
@Service
@RequiredArgsConstructor
public class RecentlyViewedService {

    private static final int MAX = 10;
    private static final Duration TTL = Duration.ofDays(7);
    private final StringRedisTemplate redis;

    private String key(String userId) { return "recent:" + userId; }

    public void recordView(String userId, String productId) {
        String k = key(userId);
        // Optional: remove existing occurrence so the list doesn't accumulate duplicates.
        redis.opsForList().remove(k, 0, productId);
        redis.opsForList().leftPush(k, productId);
        redis.opsForList().trim(k, 0, MAX - 1);
        redis.expire(k, TTL);
    }

    public List<String> getRecentlyViewed(String userId) {
        return redis.opsForList().range(key(userId), 0, MAX - 1);
    }
}
