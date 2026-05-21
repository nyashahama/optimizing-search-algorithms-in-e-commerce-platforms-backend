package com.nyasha.store.entities;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSerializationTest {

    @Test
    void productJsonDoesNotTouchLazyRelationshipGetters() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        Product product = new Product() {
            @Override
            public Supplier getSupplier() {
                throw new AssertionError("supplier should not be serialized from product search results");
            }

            @Override
            public Set<ProductVariant> getVariants() {
                throw new AssertionError("variants should not be serialized from product search results");
            }

            @Override
            public Set<Category> getCategories() {
                throw new AssertionError("categories should not be serialized from product search results");
            }

            @Override
            public Set<Review> getReviews() {
                throw new AssertionError("reviews should not be serialized from product search results");
            }
        };
        product.setProductId(1L);
        product.setName("Wireless Mouse");
        product.setSku("WM-001");
        product.setBasePrice(29.99);

        String json = objectMapper.writeValueAsString(product);

        assertThat(json).contains("\"productId\":1", "\"name\":\"Wireless Mouse\"");
        assertThat(json).doesNotContain("supplier", "variants", "categories", "reviews");
    }
}
