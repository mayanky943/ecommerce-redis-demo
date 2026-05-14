package com.learn.ecom.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Pattern 9: PUB/SUB — fire-and-forget cross-pod notifications.
 *
 *   PUBLISH order.placed "{orderId,...}"
 *
 * Best used for ephemeral notifications: cache invalidations, "wake up"
 * signals. Pub/Sub is fire-and-forget — if no subscriber is connected,
 * the message is dropped. For durable messages use Streams or Kafka.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    public static final String ORDER_PLACED_CHANNEL = "order.placed";

    private final StringRedisTemplate redis;

    public void publishOrderPlaced(String orderId) {
        log.info("[PUBLISH] {} -> {}", ORDER_PLACED_CHANNEL, orderId);
        redis.convertAndSend(ORDER_PLACED_CHANNEL, orderId);
    }
}
