package com.learn.ecom.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Document("orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order implements Serializable {
    @Id
    private String id;
    private String userId;
    @Indexed(unique = true, sparse = true)
    private String idempotencyKey;     // unique across orders; second submit with same key returns first order
    private List<CartItem> items;
    private BigDecimal total;
    private String status;             // CREATED, PAID, CANCELLED
    private Instant createdAt;
}
