package com.learn.ecom.service;

import com.learn.ecom.domain.CartItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(redisTemplate);
    }

    @Test
    void testAddItem() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        
        cartService.addItem("u1", "p1", 2);
        
        verify(hashOperations).increment("cart:u1", "p1", 2);
        verify(redisTemplate).expire(eq("cart:u1"), any(Duration.class));
    }

    @Test
    void testRemoveItem() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        
        cartService.removeItem("u1", "p1");
        
        verify(hashOperations).delete("cart:u1", "p1");
    }

    @Test
    void testGetCart() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        Map<Object, Object> mockEntries = new HashMap<>();
        mockEntries.put("p1", "2");
        mockEntries.put("p2", "5");
        when(hashOperations.entries("cart:u1")).thenReturn(mockEntries);
        
        List<CartItem> cart = cartService.getCart("u1");
        
        assertEquals(2, cart.size());
        assertTrue(cart.stream().anyMatch(item -> item.getProductId().equals("p1") && item.getQuantity() == 2));
        assertTrue(cart.stream().anyMatch(item -> item.getProductId().equals("p2") && item.getQuantity() == 5));
    }

    @Test
    void testClear() {
        cartService.clear("u1");
        verify(redisTemplate).delete("cart:u1");
    }
}
