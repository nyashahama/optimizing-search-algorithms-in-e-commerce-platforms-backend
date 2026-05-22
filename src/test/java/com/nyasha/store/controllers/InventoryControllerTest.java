package com.nyasha.store.controllers;

import com.nyasha.store.dtos.inventory.InventoryAdjustRequest;
import com.nyasha.store.dtos.inventory.InventoryResponse;
import com.nyasha.store.dtos.inventory.InventoryUpsertRequest;
import com.nyasha.store.entities.Inventory;
import com.nyasha.store.services.InventoryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryControllerTest {

    private final InventoryService inventoryService = mock(InventoryService.class);
    private final InventoryController inventoryController = new InventoryController(inventoryService);

    @Test
    void upsertDelegatesToService() {
        Inventory inventory = new Inventory();
        inventory.setQuantity(10);
        when(inventoryService.upsert(1L, null, 10, "main", 2)).thenReturn(inventory);

        InventoryResponse response = inventoryController.upsertInventory(
                1L,
                null,
                new InventoryUpsertRequest(10, "main", 2)
        ).getBody();

        assertThat(response).isNotNull();
        assertThat(response.quantity()).isEqualTo(10);
    }

    @Test
    void adjustDelegatesToService() {
        Inventory inventory = new Inventory();
        inventory.setQuantity(7);
        when(inventoryService.adjust(1L, null, -3)).thenReturn(inventory);

        InventoryResponse response = inventoryController.adjustInventory(
                1L,
                null,
                new InventoryAdjustRequest(-3)
        ).getBody();

        assertThat(response).isNotNull();
        assertThat(response.quantity()).isEqualTo(7);
        verify(inventoryService).adjust(1L, null, -3);
    }

    @Test
    void lowStockReturnsMappedResponse() {
        Inventory inventory = new Inventory();
        inventory.setQuantity(1);
        inventory.setReorderThreshold(5);
        when(inventoryService.lowStock()).thenReturn(List.of(inventory));

        assertThat(inventoryController.getLowStock())
                .hasSize(1)
                .first()
                .extracting(InventoryResponse::lowStock)
                .isEqualTo(true);
    }
}
