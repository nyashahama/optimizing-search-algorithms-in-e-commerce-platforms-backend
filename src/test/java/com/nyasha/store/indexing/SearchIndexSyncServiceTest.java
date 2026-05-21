package com.nyasha.store.indexing;

import com.nyasha.store.entities.Category;
import com.nyasha.store.entities.Product;
import com.nyasha.store.repositories.ProductRepository;
import com.nyasha.store.search.OpenSearchSearchClient;
import com.nyasha.store.search.ProductSearchDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchIndexSyncServiceTest {

    private final OpenSearchSearchClient openSearchSearchClient = mock(OpenSearchSearchClient.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final SearchIndexSyncService service = new SearchIndexSyncService(openSearchSearchClient, productRepository);

    @Test
    void indexProductLoadsProductAndMapsDocumentForOpenSearch() {
        Product product = product(10L, "Laptop", "Electronics");
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        ArgumentCaptor<ProductSearchDocument> documentCaptor = ArgumentCaptor.forClass(ProductSearchDocument.class);

        service.indexProduct(10L);

        verify(openSearchSearchClient).indexDocument(documentCaptor.capture());
        ProductSearchDocument document = documentCaptor.getValue();
        assertThat(document.productId()).isEqualTo(10L);
        assertThat(document.name()).isEqualTo("Laptop");
        assertThat(document.categories()).containsExactly("Electronics");
    }

    @Test
    void indexProductFailsWhenProductIsMissing() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.indexProduct(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found for indexing");
    }

    @Test
    void rebuildAllIndexesEveryProduct() {
        Product first = product(1L, "Laptop", "Electronics");
        Product second = product(2L, "Boots", "Shoes");
        when(productRepository.findAll()).thenReturn(List.of(first, second));

        service.rebuildAll();

        verify(openSearchSearchClient).indexDocument(ProductSearchDocument.from(first));
        verify(openSearchSearchClient).indexDocument(ProductSearchDocument.from(second));
    }

    private Product product(Long id, String name, String categoryName) {
        Category category = new Category();
        category.setName(categoryName);

        Product product = new Product();
        product.setProductId(id);
        product.setName(name);
        product.setDescription(name + " description");
        product.setCategories(Set.of(category));
        return product;
    }
}
