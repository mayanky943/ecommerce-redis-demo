package com.learn.ecom.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Pattern 5: SORTED SET — bestseller leaderboard.
 *
 *   key = leaderboard:bestsellers   value = productId   score = units sold
 *
 *   ZINCRBY leaderboard:bestsellers 1 p-1       → record a sale
 *   ZREVRANGE leaderboard:bestsellers 0 9 WITHSCORES → top 10 with counts
 *
 * Sorted Sets are O(log N) for inserts/updates and O(log N + M) for ranged
 * reads, which is why they outperform any DB-side ORDER BY for leaderboards.
 */
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private static final String KEY = "leaderboard:bestsellers";
    private final StringRedisTemplate redis;

    public void recordSale(String productId, int units) {
        redis.opsForZSet().incrementScore(KEY, productId, units);
    }

    public List<Entry> top(int n) {
        Set<TypedTuple<String>> tuples = redis.opsForZSet().reverseRangeWithScores(KEY, 0, n - 1L);
        if (tuples == null) return List.of();
        return tuples.stream()
                .map(t -> new Entry(t.getValue(), t.getScore() == null ? 0 : t.getScore().longValue()))
                .toList();
    }

    @Data
    public static class Entry {
        private final String productId;
        private final long units;
    }
}
