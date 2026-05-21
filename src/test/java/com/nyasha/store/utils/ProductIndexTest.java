package com.nyasha.store.utils;

import com.nyasha.store.entities.Category;
import com.nyasha.store.entities.Product;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProductIndexTest {

    private final ProductIndex index = new ProductIndex();

    @Test
    void searchByTextReturnsProductsMatchingNameOrDescriptionTokens() {
        Product laptop = product(1L, "Gaming Laptop", "RTX graphics and fast SSD", "SKU-1", category(10L, "Computers"));
        index.insert(laptop);

        assertThat(index.searchByText("rtx")).extracting(Product::getProductId).containsExactly(1L);
        assertThat(index.searchByText("gaming")).extracting(Product::getProductId).containsExactly(1L);
    }

    @Test
    void blankSearchesReturnEmptyResults() {
        index.insert(product(1L, "Gaming Laptop", "RTX graphics", "SKU-1", category(10L, "Computers")));

        assertThat(index.searchByText("")).isEmpty();
        assertThat(index.searchByText(null)).isEmpty();
        assertThat(index.searchByPrefix(" ")).isEmpty();
        assertThat(index.searchByCategory(null)).isEmpty();
    }

    @Test
    void searchByPrefixMatchesNameAndSku() {
        Product laptop = product(1L, "Gaming Laptop", "RTX graphics", "GL-100", category(10L, "Computers"));
        index.insert(laptop);

        assertThat(index.searchByPrefix("gam")).extracting(Product::getProductId).containsExactly(1L);
        assertThat(index.searchByPrefix("gl")).extracting(Product::getProductId).containsExactly(1L);
    }

    @Test
    void categorySearchUsesCategoryIds() {
        Product laptop = product(1L, "Gaming Laptop", "RTX graphics", "SKU-1", category(10L, "Computers"));
        index.insert(laptop);

        assertThat(index.searchByCategory("10")).extracting(Product::getProductId).containsExactly(1L);
        assertThat(index.searchByCategory("999")).isEmpty();
    }

    @Test
    void updateRemovesOldTermsAndAddsNewTerms() {
        Product oldProduct = product(1L, "Gaming Laptop", "RTX graphics", "SKU-1", category(10L, "Computers"));
        Product updatedProduct = product(1L, "Office Monitor", "4K display", "SKU-2", category(11L, "Displays"));

        index.insert(oldProduct);
        index.update(oldProduct, updatedProduct);

        assertThat(index.searchByText("gaming")).isEmpty();
        assertThat(index.searchByText("office")).extracting(Product::getProductId).containsExactly(1L);
        assertThat(index.searchByCategory("10")).isEmpty();
        assertThat(index.searchByCategory("11")).extracting(Product::getProductId).containsExactly(1L);
    }

    @Test
    void rebuildClearsStaleProducts() {
        index.insert(product(1L, "Gaming Laptop", "RTX graphics", "SKU-1", category(10L, "Computers")));

        index.rebuild(List.of(product(2L, "Office Monitor", "4K display", "SKU-2", category(11L, "Displays"))));

        assertThat(index.searchByText("gaming")).isEmpty();
        assertThat(index.searchByText("office")).extracting(Product::getProductId).containsExactly(2L);
    }

    private Product product(Long id, String name, String description, String sku, Category category) {
        Product product = new Product();
        product.setProductId(id);
        product.setName(name);
        product.setDescription(description);
        product.setSku(sku);
        product.setBasePrice(100.0);
        product.setCategories(new HashSet<>(Set.of(category)));
        return product;
    }

    private Category category(Long id, String name) {
        Category category = new Category();
        category.setCategoryId(id);
        category.setName(name);
        return category;
    }
}
