package com.learn.ecom.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * NOT a Mongo document. Lives only in Redis (Hash entry of cart:{userId}).
 * Field is `quantity` — that's what gets stored as the hash value.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItem implements Serializable {
    private String productId;
    private int quantity;
}
