package com.nyasha.store.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nyasha.store.entities.Category;
import com.nyasha.store.entities.Product;

import java.util.Set;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductSearchDocument(
        Long productId,
        String name,
        String description,
        String sku,
        String brand,
        String attributes,
        String inventoryStatus,
        Set<String> categories
) {

    public static ProductSearchDocument from(Product product) {
        return new ProductSearchDocument(
                product.getProductId(),
                product.getName(),
                product.getDescription(),
                product.getSku(),
                product.getBrand(),
                product.getAttributes(),
                product.getInventoryStatus(),
                product.getCategories() == null
                        ? Set.of()
                        : product.getCategories().stream()
                        .map(Category::getName)
                        .collect(Collectors.toSet())
        );
    }
}

