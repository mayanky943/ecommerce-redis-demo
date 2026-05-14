package com.learn.ecom.controller;

import com.learn.ecom.domain.Product;
import com.learn.ecom.service.ProductService;
import com.learn.ecom.service.RecentlyViewedService;
import com.learn.ecom.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final RecentlyViewedService recents;
    private final TagService tagService;

    /** Cache-aside: first call hits Mongo, subsequent calls hit Redis. */
    @GetMapping("/{id}")
    public ResponseEntity<Product> get(@PathVariable String id,
                                       @RequestHeader(value = "X-User-Id", required = false) String userId) {
        Product p = productService.findById(id);
        if (p == null) return ResponseEntity.notFound().build();
        if (userId != null) recents.recordView(userId, id);
        return ResponseEntity.ok(p);
    }

    @GetMapping
    public List<Product> byCategory(@RequestParam(required = false) String category) {
        return category == null ? List.of() : productService.findByCategory(category);
    }

    @PutMapping("/{id}")
    public Product update(@PathVariable String id, @RequestBody Product product) {
        product.setId(id);
        return productService.save(product);   // @CacheEvict fires
    }

    @DeleteMapping("/cache")
    public String clearCache() {
        productService.clearCache();
        return "cleared";
    }

    /** Tag a product (Pattern 4: Set). */
    @PostMapping("/{id}/tags/{tag}")
    public String tag(@PathVariable String id, @PathVariable String tag) {
        tagService.tagProduct(tag, id);
        return "tagged";
    }

    /** Products that have ALL these tags (Set intersection). */
    @GetMapping("/by-tags")
    public Set<String> byTags(@RequestParam List<String> tags) {
        return tagService.productsWithAllTags(tags.toArray(new String[0]));
    }
}
