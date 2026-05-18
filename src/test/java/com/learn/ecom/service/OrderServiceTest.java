package com.learn.ecom.service;

import com.learn.ecom.domain.CartItem;
import com.learn.ecom.domain.Order;
import com.learn.ecom.domain.Product;
import com.learn.ecom.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepo;
    @Mock private ProductService productService;
    @Mock private CartService cartService;
    @Mock private IdempotencyService idempotency;
    @Mock private DistributedLockService lock;
    @Mock private LeaderboardService leaderboard;
    @Mock private EventPublisher events;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepo, productService, cartService, idempotency, lock, leaderboard, events);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testPlaceOrderSuccess() {
        String userId = "u1";
        String idempotencyKey = "key1";
        
        when(idempotency.getExistingOrderId(idempotencyKey)).thenReturn(null);
        
        // Mock the lock to execute the task
        when(lock.withLock(anyString(), any(Duration.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<Order> task = invocation.getArgument(2);
                    return task.get();
                });

        CartItem item = new CartItem("p1", 2);
        when(cartService.getCart(userId)).thenReturn(List.of(item));
        
        Product product = Product.builder()
                .id("p1")
                .name("Product 1")
                .price(BigDecimal.valueOf(100))
                .stock(10)
                .category("cat1")
                .build();
        when(productService.findById("p1")).thenReturn(product);

        Order result = orderService.placeOrder(userId, idempotencyKey);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(BigDecimal.valueOf(200), result.getTotal());
        
        verify(orderRepo).save(any(Order.class));
        verify(idempotency).tryClaim(eq(idempotencyKey), anyString());
        verify(leaderboard).recordSale("p1", 2);
        verify(cartService).clear(userId);
        verify(events).publishOrderPlaced(anyString());
    }

    @Test
    void testPlaceOrderIdempotentFastPath() {
        String userId = "u1";
        String idempotencyKey = "key1";
        String existingOrderId = "o1";
        
        when(idempotency.getExistingOrderId(idempotencyKey)).thenReturn(existingOrderId);
        Order existingOrder = Order.builder().id(existingOrderId).build();
        when(orderRepo.findById(existingOrderId)).thenReturn(Optional.of(existingOrder));

        Order result = orderService.placeOrder(userId, idempotencyKey);

        assertEquals(existingOrder, result);
        verifyNoInteractions(lock, cartService, productService, leaderboard, events);
    }
}
