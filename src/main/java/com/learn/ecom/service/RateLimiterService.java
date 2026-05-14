package com.learn.ecom.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Pattern 6: RATE LIMIT (fixed window).
 *
 *   key = rl:{bucket}:{minute}
 *   INCR; on first writer set EXPIRE 120s (safety buffer past the window boundary)
 *
 * Trade-off: fixed window allows up to 2x burst at the boundary. For strict
 * rate-limiting use a sliding window with a Lua script or the Redis cell module.
 * Fixed window is fine for "protect downstream from abuse" use cases.
 */
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final StringRedisTemplate redis;

    public boolean allow(String bucket, int limitPerMinute) {
        long minute = Instant.now().getEpochSecond() / 60;
        String key = "rl:" + bucket + ":" + minute;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofSeconds(120));
        }
        return count != null && count <= limitPerMinute;
    }
}
