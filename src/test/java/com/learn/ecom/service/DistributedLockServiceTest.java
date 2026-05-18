package com.learn.ecom.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DistributedLockServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private DistributedLockService lockService;

    @BeforeEach
    void setUp() {
        lockService = new DistributedLockService(redisTemplate);
    }

    @Test
    void testTryAcquireSuccess() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        String token = lockService.tryAcquire("lock", Duration.ofSeconds(10));

        assertNotNull(token);
        verify(valueOperations).setIfAbsent(eq("lock"), anyString(), eq(Duration.ofSeconds(10)));
    }

    @Test
    void testTryAcquireFailure() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        String token = lockService.tryAcquire("lock", Duration.ofSeconds(10));

        assertNull(token);
    }

    @Test
    void testReleaseSuccess() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);

        boolean released = lockService.release("lock", "token");

        assertTrue(released);
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("lock")), eq("token"));
    }

    @Test
    void testReleaseFailure() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(0L);

        boolean released = lockService.release("lock", "token");

        assertFalse(released);
    }

    @Test
    void testWithLock() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);

        String result = lockService.withLock("lock", Duration.ofSeconds(10), () -> "success");

        assertEquals("success", result);
        verify(valueOperations).setIfAbsent(eq("lock"), anyString(), eq(Duration.ofSeconds(10)));
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("lock")), anyString());
    }
}
