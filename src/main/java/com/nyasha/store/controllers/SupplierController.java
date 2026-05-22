package com.nyasha.store.controllers;

import com.nyasha.store.entities.Supplier;
import com.nyasha.store.services.SupplierService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @PostMapping
    public ResponseEntity<Supplier> createSupplier(@RequestBody Supplier supplier) {
        return ResponseEntity.status(HttpStatus.CREATED).body(supplierService.createSupplier(supplier));
    }

    @GetMapping
    public List<Supplier> getSuppliers() {
        return supplierService.getSuppliers();
    }

    @GetMapping("/{supplierId}")
    public Supplier getSupplier(@PathVariable Long supplierId) {
        return supplierService.getSupplier(supplierId);
    }

    @PutMapping("/{supplierId}")
    public Supplier updateSupplier(@PathVariable Long supplierId, @RequestBody Supplier supplier) {
        return supplierService.updateSupplier(supplierId, supplier);
    }

    @DeleteMapping("/{supplierId}")
    public ResponseEntity<Void> deleteSupplier(@PathVariable Long supplierId) {
        supplierService.deleteSupplier(supplierId);
        return ResponseEntity.noContent().build();
    }
}
