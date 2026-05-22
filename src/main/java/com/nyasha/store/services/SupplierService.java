package com.nyasha.store.services;

import com.nyasha.store.entities.Supplier;
import com.nyasha.store.repositories.ProductRepository;
import com.nyasha.store.repositories.SupplierRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;

    public SupplierService(SupplierRepository supplierRepository, ProductRepository productRepository) {
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
    }

    public Supplier createSupplier(Supplier supplier) {
        requireSupplierPayload(supplier);
        supplierRepository.findByNameIgnoreCase(supplier.getName())
                .ifPresent(existing -> {
                    throw new RuntimeException("Supplier already exists with this name");
                });
        return supplierRepository.save(supplier);
    }

    public List<Supplier> getSuppliers() {
        return supplierRepository.findAll();
    }

    public Supplier getSupplier(Long supplierId) {
        return supplierRepository.findById(supplierId)
                .orElseThrow(() -> new RuntimeException("Supplier not found"));
    }

    public Supplier updateSupplier(Long supplierId, Supplier supplierDetails) {
        requireSupplierPayload(supplierDetails);
        Supplier supplier = getSupplier(supplierId);

        supplierRepository.findByNameIgnoreCase(supplierDetails.getName())
                .filter(existing -> !existing.getSupplierId().equals(supplierId))
                .ifPresent(existing -> {
                    throw new RuntimeException("Supplier already exists with this name");
                });

        supplier.setName(supplierDetails.getName());
        supplier.setContactInfo(supplierDetails.getContactInfo());
        supplier.setAddress(supplierDetails.getAddress());
        return supplierRepository.save(supplier);
    }

    public void deleteSupplier(Long supplierId) {
        Supplier supplier = getSupplier(supplierId);
        if (productRepository.existsBySupplierSupplierId(supplierId)) {
            throw new RuntimeException("Supplier cannot be deleted while linked products exist");
        }
        supplierRepository.delete(supplier);
    }

    private void requireSupplierPayload(Supplier supplier) {
        if (supplier == null) {
            throw new IllegalArgumentException("Supplier payload is required");
        }
        if (supplier.getName() == null || supplier.getName().isBlank()) {
            throw new IllegalArgumentException("Supplier name is required");
        }
    }
}
