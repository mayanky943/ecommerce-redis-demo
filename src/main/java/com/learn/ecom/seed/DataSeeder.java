package com.learn.ecom.seed;

import com.learn.ecom.domain.Product;
import com.learn.ecom.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds the products collection on startup if empty, so curl examples in the
 * README work without manual setup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final ProductRepository repo;

    @Override
    public void run(String... args) {
        if (repo.count() > 0) {
            log.info("Products already seeded, skipping. Count={}", repo.count());
            return;
        }
        List<Product> seed = List.of(
            Product.builder().id("p-1").name("iPhone 17").category("phones")
                    .price(new BigDecimal("999.00")).stock(50).description("Latest iPhone")
                    .tags(List.of("apple","5g","flagship")).build(),
            Product.builder().id("p-2").name("Galaxy S26").category("phones")
                    .price(new BigDecimal("899.00")).stock(40).description("Samsung flagship")
                    .tags(List.of("samsung","5g","flagship")).build(),
            Product.builder().id("p-3").name("Pixel 11").category("phones")
                    .price(new BigDecimal("799.00")).stock(30).description("Google Pixel")
                    .tags(List.of("google","5g","ai")).build(),
            Product.builder().id("p-4").name("AirPods Pro 3").category("audio")
                    .price(new BigDecimal("249.00")).stock(120).description("Noise-cancelling earbuds")
                    .tags(List.of("apple","wireless","anc")).build(),
            Product.builder().id("p-5").name("MagSafe Charger").category("accessories")
                    .price(new BigDecimal("39.00")).stock(500).description("15W wireless charger")
                    .tags(List.of("apple","wireless","accessory")).build()
        );
        repo.saveAll(seed);
        log.info("Seeded {} products", seed.size());
    }
}
