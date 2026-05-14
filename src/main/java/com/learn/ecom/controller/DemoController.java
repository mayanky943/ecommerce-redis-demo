package com.learn.ecom.controller;

import com.learn.ecom.service.DistributedLockService;
import com.learn.ecom.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * Endpoints purely for demonstrating Redis primitives in isolation.
 */
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

    private final RateLimiterService limiter;
    private final DistributedLockService lock;

    /**
     * Pattern 6: rate-limited endpoint. Default 5 requests / minute per bucket.
     * Try: `for i in 1..10; curl ... ; done` — the 6th–10th calls return 429.
     */
    @GetMapping("/rate-limited")
    public ResponseEntity<String> rateLimited(@RequestParam(defaultValue = "default") String bucket,
                                              @RequestParam(defaultValue = "5") int perMinute) {
        if (!limiter.allow(bucket, perMinute)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("rate limit exceeded");
        }
        return ResponseEntity.ok("ok");
    }

    /**
     * Pattern 7: try to grab a lock with the given key. First caller gets the
     * token; subsequent callers within the TTL get 409.
     */
    @PostMapping("/lock/{key}")
    public ResponseEntity<String> tryLock(@PathVariable String key,
                                          @RequestParam(defaultValue = "10") int ttlSeconds) {
        String token = lock.tryAcquire("lock:" + key, Duration.ofSeconds(ttlSeconds));
        if (token == null) return ResponseEntity.status(HttpStatus.CONFLICT).body("locked");
        return ResponseEntity.ok("acquired token=" + token);
    }

    @DeleteMapping("/lock/{key}")
    public ResponseEntity<String> releaseLock(@PathVariable String key, @RequestParam String token) {
        boolean ok = lock.release("lock:" + key, token);
        return ok ? ResponseEntity.ok("released") : ResponseEntity.status(HttpStatus.GONE).body("not your lock");
    }
}
