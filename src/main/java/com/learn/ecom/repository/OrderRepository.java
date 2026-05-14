package com.learn.ecom.repository;

import com.learn.ecom.domain.Order;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order, String> {
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
}
