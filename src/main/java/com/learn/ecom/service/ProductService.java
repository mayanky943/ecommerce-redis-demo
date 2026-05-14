package com.learn.ecom.service;

import com.learn.ecom.domain.Product;
import com.learn.ecom.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Pattern 1: CACHE-ASIDE with @Cacheable / @CacheEvict.
 *
 *   - First call:    MISS → MongoDB read → store in Redis under key  products::<id>
 *   - Repeat calls:  HIT  → Redis read → never touches Mongo
 *   - On update:     @CacheEvict wipes the entry so the next read re-fills from Mongo.
 *
 * TTL for the "products" cache is set in RedisConfig (30 min).
 *
 * The `unless = "#result == null"` prevents caching a null when an ID doesn't exist
 * (that would be cache penetration — Pattern 9 in the README).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository repo;

    @Cacheable(cacheNames = "products", key = "#id", unless = "#result == null")
    public Product findById(String id) {
        log.info("[CACHE MISS] hitting MongoDB for product id={}", id);
        return repo.findById(id).orElse(null);
    }

    public List<Product> findByCategory(String category) {
        return repo.findByCategory(category);
    }

    @CacheEvict(cacheNames = "products", key = "#product.id")
    public Product save(Product product) {
        log.info("[CACHE EVICT] saving and evicting product id={}", product.getId());
        return repo.save(product);
    }

    @CacheEvict(cacheNames = "products", allEntries = true)
    public void clearCache() {
        log.info("[CACHE EVICT] clearing entire products cache");
    }
}
