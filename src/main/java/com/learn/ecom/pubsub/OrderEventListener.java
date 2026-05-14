package com.learn.ecom.pubsub;

import com.learn.ecom.service.EventPublisher;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Subscribes to the order.placed channel and reacts. In a real system this
 * is where you'd trigger an email, invalidate a related cache entry, or kick
 * off a downstream workflow.
 *
 * Two ways to write a Redis listener:
 *   (a) Implement MessageListener (this file) — closer to the metal.
 *   (b) @RedisListener / message-driven POJO via @MessageMapping if you adopt
 *       higher-level Spring Integration. (a) is fine for a demo.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener implements MessageListener {

    private final RedisMessageListenerContainer container;

    @PostConstruct
    public void subscribe() {
        container.addMessageListener(this, new PatternTopic(EventPublisher.ORDER_PLACED_CHANNEL));
        log.info("Subscribed to channel {}", EventPublisher.ORDER_PLACED_CHANNEL);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String orderId = new String(message.getBody());
        log.info("[SUBSCRIBE] received {} on channel {}", orderId, new String(message.getChannel()));
        // In reality: send confirmation email, update analytics, invalidate cache, etc.
    }
}
