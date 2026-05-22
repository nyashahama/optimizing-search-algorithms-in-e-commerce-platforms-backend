package com.nyasha.store.services;

import com.nyasha.store.entities.Inventory;
import com.nyasha.store.entities.Product;
import com.nyasha.store.entities.ProductVariant;
import com.nyasha.store.repositories.InventoryRepository;
import com.nyasha.store.repositories.ProductRepository;
import com.nyasha.store.repositories.ProductVariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;

    public InventoryService(
            InventoryRepository inventoryRepository,
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository
    ) {
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
    }

    public int availableQuantity(Long productId, Long variantId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is required");
        }
        return inventoryRepository.findForUpdate(productId, variantId)
                .map(item -> item.getQuantity() == null ? 0 : item.getQuantity())
                .orElse(0);
    }

    public void reserve(Long productId, Long variantId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be a positive integer");
        }

        Inventory inventory = inventoryRepository.findForUpdate(productId, variantId)
                .orElseThrow(() -> new RuntimeException("Inventory entry not found"));
        int available = inventory.getQuantity() == null ? 0 : inventory.getQuantity();
        if (available < quantity) {
            throw new RuntimeException("Insufficient stock");
        }
        inventory.setQuantity(available - quantity);
        inventoryRepository.save(inventory);
    }

    public void release(Long productId, Long variantId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be a positive integer");
        }

        Inventory inventory = inventoryRepository.findForUpdate(productId, variantId)
                .orElseThrow(() -> new RuntimeException("Inventory entry not found"));

        int available = inventory.getQuantity() == null ? 0 : inventory.getQuantity();
        inventory.setQuantity(Math.addExact(available, quantity));
        inventoryRepository.save(inventory);
    }

    public void releaseIfPresent(Long productId, Long variantId, int quantity) {
        if (productId == null || quantity <= 0) {
            return;
        }
        inventoryRepository.findForUpdate(productId, variantId)
                .ifPresent(inventory -> {
                    int current = inventory.getQuantity() == null ? 0 : inventory.getQuantity();
                    inventory.setQuantity(Math.addExact(current, quantity));
                    inventoryRepository.save(inventory);
                });
    }

    @Transactional
    public Inventory upsert(Long productId, Long variantId, int quantity, String location, Integer reorderThreshold) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is required");
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity cannot be negative");
        }
        if (reorderThreshold != null && reorderThreshold < 0) {
            throw new IllegalArgumentException("reorderThreshold cannot be negative");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        ProductVariant variant = resolveVariant(productId, variantId);

        Inventory inventory = inventoryRepository.findForUpdate(productId, variantId)
                .orElseGet(() -> {
                    Inventory created = new Inventory();
                    created.setProduct(product);
                    created.setVariant(variant);
                    return created;
                });

        inventory.setQuantity(quantity);
        inventory.setLocation(location);
        inventory.setReorderThreshold(reorderThreshold);
        return inventoryRepository.save(inventory);
    }

    @Transactional
    public Inventory adjust(Long productId, Long variantId, int delta) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is required");
        }
        if (delta == 0) {
            throw new IllegalArgumentException("delta cannot be zero");
        }

        Inventory inventory = inventoryRepository.findForUpdate(productId, variantId)
                .orElseThrow(() -> new RuntimeException("Inventory entry not found"));
        int current = inventory.getQuantity() == null ? 0 : inventory.getQuantity();
        int updated = Math.addExact(current, delta);
        if (updated < 0) {
            throw new RuntimeException("Insufficient stock for adjustment");
        }
        inventory.setQuantity(updated);
        return inventoryRepository.save(inventory);
    }

    public Inventory getInventory(Long productId, Long variantId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is required");
        }
        return inventoryRepository.findByProductAndVariant(productId, variantId)
                .orElseThrow(() -> new RuntimeException("Inventory entry not found"));
    }

    public List<Inventory> lowStock() {
        return inventoryRepository.findLowStock();
    }

    private ProductVariant resolveVariant(Long productId, Long variantId) {
        if (variantId == null) {
            return null;
        }

        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new RuntimeException("Product variant not found"));
        if (variant.getProduct() == null || variant.getProduct().getProductId() == null
                || !variant.getProduct().getProductId().equals(productId)) {
            throw new RuntimeException("Variant does not belong to product");
        }
        return variant;
    }
}
