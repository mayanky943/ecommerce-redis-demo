package com.learn.ecom.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Central Redis wiring. Two things matter here:
 *
 *  1) RedisTemplate<String, Object> — string keys, JSON values. Used by services
 *     that want manual control (Hash, List, Set, ZSet, lock, idempotency).
 *
 *  2) CacheManager — backs Spring's @Cacheable / @CacheEvict. Per-cache TTLs
 *     are declared here so a domain object can expire on its own schedule.
 *
 * Serializer is GenericJackson2JsonRedisSerializer so values stored in Redis are
 * human-readable JSON — open redis-cli and run `GET product:p-1` to verify.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory cf,
                                                       ObjectMapper mapper) {
        var json = new GenericJackson2JsonRedisSerializer(mapper);
        RedisTemplate<String, Object> tpl = new RedisTemplate<>();
        tpl.setConnectionFactory(cf);
        tpl.setKeySerializer(new StringRedisSerializer());
        tpl.setValueSerializer(json);
        tpl.setHashKeySerializer(new StringRedisSerializer());
        tpl.setHashValueSerializer(json);
        tpl.afterPropertiesSet();
        return tpl;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory cf) {
        return new StringRedisTemplate(cf);
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory cf, ObjectMapper mapper) {
        var json = new GenericJackson2JsonRedisSerializer(mapper);

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(json));

        // Per-cache TTLs. Demonstrates the principle: cache "stale tolerance" varies by data.
        Map<String, RedisCacheConfiguration> perCache = Map.of(
            "products",   base.entryTtl(Duration.ofMinutes(30)),   // catalog changes rarely
            "categories", base.entryTtl(Duration.ofHours(2)),      // categories change even less
            "userPrefs",  base.entryTtl(Duration.ofMinutes(5))     // preferences change often
        );

        return RedisCacheManager.builder(cf)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    /**
     * Container that fans Redis pub/sub messages out to @MessageListener-style beans.
     * Channels are registered in OrderEventListener via subscriptions.
     */
    @Bean
    public RedisMessageListenerContainer messageListenerContainer(RedisConnectionFactory cf) {
        RedisMessageListenerContainer c = new RedisMessageListenerContainer();
        c.setConnectionFactory(cf);
        return c;
    }
}
