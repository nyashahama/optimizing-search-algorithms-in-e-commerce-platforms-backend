package com.nyasha.store.services;

import com.nyasha.store.entities.Inventory;
import com.nyasha.store.entities.ProductVariant;
import com.nyasha.store.repositories.InventoryRepository;
import com.nyasha.store.repositories.ProductRepository;
import com.nyasha.store.repositories.ProductVariantRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void availableQuantityReturnsZeroWhenInventoryMissing() {
        when(inventoryRepository.findForUpdate(10L, 20L)).thenReturn(Optional.empty());
        assertThat(inventoryService.availableQuantity(10L, 20L)).isZero();
    }

    @Test
    void availableQuantityReturnsExistingQuantity() {
        when(inventoryRepository.findForUpdate(10L, null)).thenReturn(Optional.of(inventory(10L, null, 7)));
        assertThat(inventoryService.availableQuantity(10L, null)).isEqualTo(7);
    }

    @Test
    void reserveDecreasesAvailableStock() {
        Inventory inventory = inventory(10L, null, 8);
        when(inventoryRepository.findForUpdate(10L, null)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(inventory)).thenReturn(inventory);

        inventoryService.reserve(10L, null, 5);

        assertThat(inventory.getQuantity()).isEqualTo(3);
        verify(inventoryRepository).save(inventory);
    }

    @Test
    void reserveRejectsOversellAttempt() {
        Inventory inventory = inventory(10L, null, 2);
        when(inventoryRepository.findForUpdate(10L, null)).thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> inventoryService.reserve(10L, null, 3))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void releaseIncreasesAvailableStock() {
        Inventory inventory = inventory(10L, null, 2);
        when(inventoryRepository.findForUpdate(10L, null)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(inventory)).thenReturn(inventory);

        inventoryService.release(10L, null, 5);

        assertThat(inventory.getQuantity()).isEqualTo(7);
        verify(inventoryRepository).save(inventory);
    }

    @Test
    void upsertCreatesInventoryWhenMissing() {
        com.nyasha.store.entities.Product product = product(10L);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(inventoryRepository.findForUpdate(10L, null)).thenReturn(Optional.empty());
        when(inventoryRepository.save(org.mockito.ArgumentMatchers.any(Inventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Inventory saved = inventoryService.upsert(10L, null, 25, "main", 5);

        assertThat(saved.getProduct().getProductId()).isEqualTo(10L);
        assertThat(saved.getQuantity()).isEqualTo(25);
        assertThat(saved.getLocation()).isEqualTo("main");
        assertThat(saved.getReorderThreshold()).isEqualTo(5);
    }

    @Test
    void upsertRejectsVariantBelongingToAnotherProduct() {
        com.nyasha.store.entities.Product product = product(10L);
        com.nyasha.store.entities.Product other = product(99L);
        ProductVariant variant = new ProductVariant();
        variant.setVariantId(7L);
        variant.setProduct(other);

        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productVariantRepository.findById(7L)).thenReturn(Optional.of(variant));

        assertThatThrownBy(() -> inventoryService.upsert(10L, 7L, 10, null, 3))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Variant does not belong to product");
    }

    @Test
    void adjustIncreasesAndDecreasesStock() {
        Inventory inventory = inventory(10L, null, 3);
        when(inventoryRepository.findForUpdate(10L, null)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(inventory)).thenReturn(inventory);

        inventoryService.adjust(10L, null, 2);
        assertThat(inventory.getQuantity()).isEqualTo(5);

        inventoryService.adjust(10L, null, -4);
        assertThat(inventory.getQuantity()).isEqualTo(1);
    }

    @Test
    void adjustRejectsNegativeResult() {
        Inventory inventory = inventory(10L, null, 2);
        when(inventoryRepository.findForUpdate(10L, null)).thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> inventoryService.adjust(10L, null, -3))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void lowStockReturnsRepositoryResult() {
        Inventory low = inventory(10L, null, 1);
        low.setReorderThreshold(5);
        when(inventoryRepository.findLowStock()).thenReturn(List.of(low));

        assertThat(inventoryService.lowStock()).containsExactly(low);
    }

    private Inventory inventory(Long productId, Long variantId, int quantity) {
        Inventory inventory = new Inventory();
        inventory.setProduct(product(productId));
        inventory.setQuantity(quantity);
        return inventory;
    }

    private com.nyasha.store.entities.Product product(Long id) {
        com.nyasha.store.entities.Product product = new com.nyasha.store.entities.Product();
        product.setProductId(id);
        return product;
    }
}
