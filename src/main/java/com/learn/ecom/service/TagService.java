package com.learn.ecom.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Pattern 4: SET — tag-based product membership and set algebra.
 *
 *   key = tag:{tagName}   value = set of productIds
 *
 *   SADD tag:apple p-1 p-4 p-5
 *   SMEMBERS tag:apple              → all apple products
 *   SINTER tag:apple tag:wireless   → products that are both apple AND wireless
 *
 * Demonstrates membership queries the relational way would be a JOIN.
 */
@Service
@RequiredArgsConstructor
public class TagService {

    private final StringRedisTemplate redis;

    private String key(String tag) { return "tag:" + tag; }

    public void tagProduct(String tag, String productId) {
        redis.opsForSet().add(key(tag), productId);
    }

    public Set<String> productsWithTag(String tag) {
        return redis.opsForSet().members(key(tag));
    }

    /** Set intersection — "products with ALL of these tags". */
    public Set<String> productsWithAllTags(String... tags) {
        if (tags.length == 0) return Set.of();
        String first = key(tags[0]);
        java.util.List<String> rest = new java.util.ArrayList<>();
        for (int i = 1; i < tags.length; i++) rest.add(key(tags[i]));
        return redis.opsForSet().intersect(first, rest);
    }
}
