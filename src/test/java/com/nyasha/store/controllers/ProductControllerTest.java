package com.nyasha.store.controllers;

import com.nyasha.store.entities.Category;
import com.nyasha.store.entities.Product;
import com.nyasha.store.search.ProductSearchService;
import com.nyasha.store.search.SearchEngineType;
import com.nyasha.store.search.SearchResult;
import com.nyasha.store.services.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductControllerTest {

    private final ProductService productService = mock(ProductService.class);
    private final ProductSearchService productSearchService = mock(ProductSearchService.class);
    private final ProductController productController = new ProductController(productService, productSearchService);

    @Test
    void createProductReturnsCreated() {
        Product product = product(1L, "Demo", "SKU-1", 19.99);
        when(productService.createProduct(product)).thenReturn(product);

        ResponseEntity<Product> response = productController.createProduct(product);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(product);
    }

    @Test
    void allProductReadOperationsDelegateToService() {
        Product single = product(1L, "Demo", "SKU-1", 19.99);
        Category category = new Category();
        category.setCategoryId(4L);
        when(productService.getAllProducts()).thenReturn(List.of(single));
        when(productService.getProductById(1L)).thenReturn(single);
        when(productService.updateProduct(1L, single)).thenReturn(single);
        when(productService.searchByText("wireless")).thenReturn(Set.of(single));
        when(productService.autocomplete("wire")).thenReturn(List.of(single));
        when(productService.getProductsByCategory("headphones")).thenReturn(List.of(single));
        when(productSearchService.search("in_memory", "wireless", 20))
                .thenReturn(SearchResult.success(SearchEngineType.IN_MEMORY, 3L, List.of(single)));

        assertThat(productController.getAllProducts().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(productController.getAllProducts().getBody()).containsExactly(single);
        assertThat(productController.getProductById(1L).getBody()).isSameAs(single);
        assertThat(productController.updateProduct(1L, single).getBody()).isSameAs(single);
        assertThat(productController.deleteProduct(1L).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(productController.searchByText("wireless").getBody()).containsExactly(single);
        assertThat(productController.autocomplete("wire").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(productController.getProductsByCategory("headphones").getBody()).containsExactly(single);
    }

    @Test
    void searchByTextUsesSearchServiceEngineInMemory() {
        SearchResult result = SearchResult.success(SearchEngineType.IN_MEMORY, 3L, List.of());
        when(productSearchService.search("in_memory", "query", 20)).thenReturn(result);

        Set<Product> products = productController.searchByText("query").getBody();

        assertThat(products).isNotNull();
        assertThat(products).isEqualTo(Set.of());
        assertThat(result.products()).isEmpty();
    }

    private Product product(Long id, String name, String sku, double basePrice) {
        Product product = new Product();
        product.setProductId(id);
        product.setName(name);
        product.setSku(sku);
        product.setBasePrice(basePrice);
        return product;
    }
}
