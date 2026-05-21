package com.nyasha.store.indexing;

import com.nyasha.store.entities.Product;
import com.nyasha.store.repositories.ProductRepository;
import com.nyasha.store.search.OpenSearchSearchClient;
import com.nyasha.store.search.ProductSearchDocument;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SearchIndexSyncService {

    private final OpenSearchSearchClient openSearchSearchClient;
    private final ProductRepository productRepository;

    public SearchIndexSyncService(
            OpenSearchSearchClient openSearchSearchClient,
            ProductRepository productRepository
    ) {
        this.openSearchSearchClient = openSearchSearchClient;
        this.productRepository = productRepository;
    }

    public void indexProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found for indexing: " + productId));
        openSearchSearchClient.indexDocument(ProductSearchDocument.from(product));
    }

    public void deleteProduct(Long productId) {
        openSearchSearchClient.deleteDocument(productId);
    }

    public long documentCount() {
        return openSearchSearchClient.countDocuments();
    }

    public List<String> suggest(String prefix, int limit) {
        return openSearchSearchClient.suggest(prefix, limit);
    }

    public void rebuildAll() {
        List<Product> products = productRepository.findAll();
        for (Product product : products) {
            openSearchSearchClient.indexDocument(ProductSearchDocument.from(product));
        }
    }
}
