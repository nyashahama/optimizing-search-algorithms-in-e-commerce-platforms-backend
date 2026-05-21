package com.nyasha.store.services;

import com.nyasha.store.entities.Product;
import com.nyasha.store.repositories.ProductRepository;
import com.nyasha.store.utils.ProductIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductIndex productIndex;

    @InjectMocks
    private ProductService productService;

    @Test
    void initializeIndexRebuildsFromRepositoryProducts() {
        Product product = product(1L, "Laptop", "Fast laptop", "SKU-1", 100.0);
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(productRepository.count()).thenReturn(1L);

        productService.initializeIndex();

        verify(productIndex).rebuild(List.of(product));
    }

    @Test
    void createProductValidatesPayloadBeforeRepositoryWrite() {
        Product invalid = product(null, "", "No name", "SKU-1", 100.0);

        assertThatThrownBy(() -> productService.createProduct(invalid))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Product name is required");

        verifyNoInteractions(productRepository);
        verifyNoInteractions(productIndex);
    }

    @Test
    void createProductSavesAndIndexesValidProduct() {
        Product product = product(null, "Laptop", "Fast laptop", "SKU-1", 100.0);
        Product saved = product(1L, "Laptop", "Fast laptop", "SKU-1", 100.0);
        when(productRepository.save(product)).thenReturn(saved);

        Product result = productService.createProduct(product);

        assertThat(result.getProductId()).isEqualTo(1L);
        verify(productIndex).insert(saved);
    }

    @Test
    void getProductsByNumericCategoryUsesRepositoryQuery() {
        Product product = product(1L, "Laptop", "Fast laptop", "SKU-1", 100.0);
        when(productRepository.findByCategoryId(10L)).thenReturn(List.of(product));

        List<Product> results = productService.getProductsByCategory("10");

        assertThat(results).containsExactly(product);
        verify(productRepository).findByCategoryId(10L);
    }

    @Test
    void updateProductFailsWhenProductDoesNotExist() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(99L, product(null, "Laptop", "Fast", "SKU-1", 100.0)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Product update failed");
    }

    private Product product(Long id, String name, String description, String sku, Double basePrice) {
        Product product = new Product();
        product.setProductId(id);
        product.setName(name);
        product.setDescription(description);
        product.setSku(sku);
        product.setBasePrice(basePrice);
        return product;
    }
}
