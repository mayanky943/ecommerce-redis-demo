package com.learn.ecom.controller;

import com.learn.ecom.domain.Order;
import com.learn.ecom.repository.OrderRepository;
import com.learn.ecom.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderRepository orderRepo;

    /**
     * Place an order for a user. Caller MUST supply an Idempotency-Key header.
     * Second call with the same key returns the original order — no duplicate.
     *
     * Demonstrates: Idempotency (Pattern 8) + Distributed lock (Pattern 7)
     *             + Leaderboard update (Pattern 5) + Pub/Sub (Pattern 9).
     */
    @PostMapping
    public ResponseEntity<Order> place(@RequestParam String userId,
                                       @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ResponseEntity.ok(orderService.placeOrder(userId, idempotencyKey));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> get(@PathVariable String id) {
        return orderRepo.findById(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
