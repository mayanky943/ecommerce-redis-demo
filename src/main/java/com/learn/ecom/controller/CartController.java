package com.learn.ecom.controller;

import com.learn.ecom.domain.CartItem;
import com.learn.ecom.service.CartService;
import com.learn.ecom.service.RecentlyViewedService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/carts/{userId}")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final RecentlyViewedService recents;

    @GetMapping
    public List<CartItem> get(@PathVariable String userId) {
        return cartService.getCart(userId);
    }

    @PostMapping("/items")
    public List<CartItem> add(@PathVariable String userId, @RequestBody CartItem item) {
        cartService.addItem(userId, item.getProductId(), item.getQuantity());
        return cartService.getCart(userId);
    }

    @DeleteMapping("/items/{productId}")
    public List<CartItem> remove(@PathVariable String userId, @PathVariable String productId) {
        cartService.removeItem(userId, productId);
        return cartService.getCart(userId);
    }

    @DeleteMapping
    public String clear(@PathVariable String userId) {
        cartService.clear(userId);
        return "cleared";
    }

    @GetMapping("/recent")
    public List<String> recent(@PathVariable String userId) {
        return recents.getRecentlyViewed(userId);
    }
}
