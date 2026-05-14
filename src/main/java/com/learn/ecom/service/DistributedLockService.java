package com.learn.ecom.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Pattern 7: DISTRIBUTED LOCK (SETNX + Lua release).
 *
 *   acquire: SET key uniqueToken NX EX <ttl>     — atomic "set if absent" with TTL
 *   release: Lua that DELs the key only if its value still equals our token
 *            (prevents "I acquired, then went to sleep past the TTL, someone
 *             else acquired it, and now I'd DEL their lock")
 *
 * Use cases: scheduled jobs running on N pods where only one should execute,
 * leader election, per-resource serialization.
 *
 * For higher-correctness across a Redis cluster (master-replica failover can
 * lose acquired locks), use Redlock across 5 independent nodes. For most
 * within-one-replica cases the pattern below is fine.
 */
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] " +
        "then return redis.call('del', KEYS[1]) else return 0 end",
        Long.class
    );

    private final StringRedisTemplate redis;

    /** Returns the ownership token on success, or null if lock was held. */
    public String tryAcquire(String lockKey, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean ok = redis.opsForValue().setIfAbsent(lockKey, token, ttl);
        return Boolean.TRUE.equals(ok) ? token : null;
    }

    public boolean release(String lockKey, String token) {
        Long deleted = redis.execute(RELEASE, List.of(lockKey), token);
        return deleted != null && deleted == 1L;
    }

    /**
     * Convenience: run task under the lock if acquired, otherwise return null.
     */
    public <T> T withLock(String lockKey, Duration ttl, java.util.function.Supplier<T> task) {
        String token = tryAcquire(lockKey, ttl);
        if (token == null) return null;
        try {
            return task.get();
        } finally {
            release(lockKey, token);
        }
    }
}
