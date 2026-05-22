package com.nyasha.store.services;

import com.nyasha.store.entities.Supplier;
import com.nyasha.store.repositories.ProductRepository;
import com.nyasha.store.repositories.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private SupplierService supplierService;

    @Test
    void createSupplierPersistsEntity() {
        Supplier supplier = supplier("Acme");
        when(supplierRepository.findByNameIgnoreCase("Acme")).thenReturn(Optional.empty());
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Supplier created = supplierService.createSupplier(supplier);

        assertThat(created.getName()).isEqualTo("Acme");
        verify(supplierRepository).save(any(Supplier.class));
    }

    @Test
    void createSupplierRejectsDuplicateName() {
        when(supplierRepository.findByNameIgnoreCase("Acme")).thenReturn(Optional.of(supplier("Acme")));

        assertThatThrownBy(() -> supplierService.createSupplier(supplier("Acme")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void updateSupplierChangesFields() {
        Supplier existing = supplier("Acme");
        existing.setSupplierId(10L);
        when(supplierRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(supplierRepository.findByNameIgnoreCase("Acme Updated")).thenReturn(Optional.empty());
        when(supplierRepository.save(existing)).thenReturn(existing);

        Supplier update = supplier("Acme Updated");
        update.setAddress("New address");
        update.setContactInfo("support@acme.com");

        Supplier updated = supplierService.updateSupplier(10L, update);

        assertThat(updated.getName()).isEqualTo("Acme Updated");
        assertThat(updated.getAddress()).isEqualTo("New address");
        assertThat(updated.getContactInfo()).isEqualTo("support@acme.com");
    }

    @Test
    void deleteSupplierRejectedWhenProductsStillLinked() {
        Supplier existing = supplier("Acme");
        existing.setSupplierId(10L);
        when(supplierRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(productRepository.existsBySupplierSupplierId(10L)).thenReturn(true);

        assertThatThrownBy(() -> supplierService.deleteSupplier(10L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("linked products");
        verify(supplierRepository, never()).delete(existing);
    }

    private Supplier supplier(String name) {
        Supplier supplier = new Supplier();
        supplier.setName(name);
        supplier.setAddress("Address");
        supplier.setContactInfo("Contact");
        return supplier;
    }
}
