package com.learn.ecom.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Product stored in MongoDB and cached in Redis.
 *
 * Implements Serializable so Spring Data Redis can round-trip via JSON without
 * surprises on nested types. GenericJackson2JsonRedisSerializer adds a @class
 * marker so deserialization knows what type to instantiate.
 */
@Document("products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product implements Serializable {
    @Id
    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private int stock;
    private String category;
    private List<String> tags;
}
