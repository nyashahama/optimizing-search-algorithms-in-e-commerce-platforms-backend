package com.nyasha.store.controllers;

import com.nyasha.store.entities.Supplier;
import com.nyasha.store.services.SupplierService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupplierControllerTest {

    private final SupplierService supplierService = mock(SupplierService.class);
    private final SupplierController supplierController = new SupplierController(supplierService);

    @Test
    void createSupplierDelegatesToService() {
        Supplier supplier = supplier("Acme");
        when(supplierService.createSupplier(supplier)).thenReturn(supplier);

        assertThat(supplierController.createSupplier(supplier).getBody()).isSameAs(supplier);
    }

    @Test
    void listSuppliersReturnsServiceResponse() {
        Supplier supplier = supplier("Acme");
        when(supplierService.getSuppliers()).thenReturn(List.of(supplier));

        assertThat(supplierController.getSuppliers()).containsExactly(supplier);
        verify(supplierService).getSuppliers();
    }

    private Supplier supplier(String name) {
        Supplier supplier = new Supplier();
        supplier.setName(name);
        return supplier;
    }
}
