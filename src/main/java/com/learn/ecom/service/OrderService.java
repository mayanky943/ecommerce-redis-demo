package com.learn.ecom.service;

import com.learn.ecom.domain.CartItem;
import com.learn.ecom.domain.Order;
import com.learn.ecom.domain.Product;
import com.learn.ecom.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Combines several Redis patterns to place an order:
 *
 *  - IDEMPOTENCY (Pattern 8): same idempotencyKey → return the first order, no dupe.
 *  - DISTRIBUTED LOCK (Pattern 7): serialize concurrent submits for the SAME user
 *    so we don't double-debit stock if the client retries the API call from two tabs.
 *  - LEADERBOARD update (Pattern 5): increment bestseller score per ordered unit.
 *  - PUB/SUB (Pattern 9): broadcast an event so any subscriber can react.
 *  - CACHE EVICT: orderService doesn't directly touch the products cache here,
 *    but in a real system you'd evict the product's cache entry if stock changed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepo;
    private final ProductService productService;
    private final CartService cartService;
    private final IdempotencyService idempotency;
    private final DistributedLockService lock;
    private final LeaderboardService leaderboard;
    private final EventPublisher events;

    public Order placeOrder(String userId, String idempotencyKey) {
        // 1) Idempotency: fast-path return if the same key was seen before.
        String existingId = idempotency.getExistingOrderId(idempotencyKey);
        if (existingId != null) {
            log.info("[IDEMPOTENT] returning existing order {} for key {}", existingId, idempotencyKey);
            return orderRepo.findById(existingId).orElseThrow();
        }

        // 2) Lock so two concurrent requests for the same user don't both proceed.
        return lock.withLock("lock:placeOrder:" + userId, Duration.ofSeconds(10), () -> {
            // 2a) Re-check idempotency inside the lock (race-condition double-check).
            String secondCheck = idempotency.getExistingOrderId(idempotencyKey);
            if (secondCheck != null) return orderRepo.findById(secondCheck).orElseThrow();

            // 3) Build order from cart.
            List<CartItem> items = cartService.getCart(userId);
            if (items.isEmpty()) {
                throw new IllegalStateException("Cart is empty for user " + userId);
            }
            BigDecimal total = BigDecimal.ZERO;
            for (CartItem item : items) {
                Product p = productService.findById(item.getProductId());
                if (p == null) throw new IllegalStateException("Unknown product " + item.getProductId());
                total = total.add(p.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            }

            Order order = Order.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .idempotencyKey(idempotencyKey)
                    .items(items)
                    .total(total)
                    .status("CREATED")
                    .createdAt(Instant.now())
                    .build();
            orderRepo.save(order);

            // 4) Claim the idempotency key now that the order is persisted.
            idempotency.tryClaim(idempotencyKey, order.getId());

            // 5) Bestseller leaderboard update.
            items.forEach(it -> leaderboard.recordSale(it.getProductId(), it.getQuantity()));

            // 6) Clear the cart and broadcast.
            cartService.clear(userId);
            events.publishOrderPlaced(order.getId());

            return order;
        });
    }
}
