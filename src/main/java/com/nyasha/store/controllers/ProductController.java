package com.nyasha.store.controllers;

import com.nyasha.store.entities.Product;
import com.nyasha.store.search.ProductSearchService;
import com.nyasha.store.services.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final ProductSearchService productSearchService;

    @Autowired
    public ProductController(ProductService productService, ProductSearchService productSearchService) {
        this.productService = productService;
        this.productSearchService = productSearchService;
    }

    // Create a product
    @PostMapping
    public ResponseEntity<Product> createProduct(@RequestBody Product product) {
        Product createdProduct = productService.createProduct(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdProduct);
    }

    // Get all products
    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    // Get product by ID
    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    // Update a product
    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(
            @PathVariable Long id,
            @RequestBody Product productDetails
    ) {
        return ResponseEntity.ok(productService.updateProduct(id, productDetails));
    }

    // Delete a product
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    // Full-text search
    @GetMapping("/search")
    public ResponseEntity<Set<Product>> searchByText(@RequestParam String query) {
        return ResponseEntity.ok(productSearchService.search("in_memory", query, 20).products()
                .stream().collect(Collectors.toSet()));
    }

    // Autocomplete suggestions
    @GetMapping("/autocomplete")
    public ResponseEntity<List<Product>> autocomplete(@RequestParam String prefix) {
        return ResponseEntity.ok(productService.autocomplete(prefix));
    }

    // Get products by category
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<Product>> getProductsByCategory(@PathVariable String categoryId) {
        return ResponseEntity.ok(productService.getProductsByCategory(categoryId));
    }
}
