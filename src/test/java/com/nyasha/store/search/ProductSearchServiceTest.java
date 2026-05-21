package com.nyasha.store.search;

import com.nyasha.store.entities.Product;
import com.nyasha.store.repositories.ProductRepository;
import com.nyasha.store.utils.ProductIndex;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductSearchServiceTest {

    @Test
    void defaultEngineFallsBackToInMemoryWithNormalizedLimit() {
        ProductRepository productRepository = mock(ProductRepository.class);
        ProductIndex productIndex = mock(ProductIndex.class);

        ProductSearchService service = new ProductSearchService(List.of(
                new SqlLikeProductSearchEngine(productRepository),
                new InMemoryProductSearchEngine(productIndex),
                new PostgresFullTextSearchEngine(),
                new OpenSearchProductSearchEngine()
        ));

        when(productIndex.searchByText("shoe")).thenReturn(List.of(product(1L, "Sneaker"), product(2L, "Shoe rack")));

        var result = service.search("inmemory", "shoe", 1);

        assertThat(result.supported()).isTrue();
        assertThat(result.count()).isEqualTo(1);
        assertThat(result.products()).extracting(Product::getProductId).containsExactly(1L);
    }

    @Test
    void sqlLikeUsesConfiguredLimitAndRepositoryQuery() {
        ProductRepository productRepository = mock(ProductRepository.class);
        ProductIndex productIndex = mock(ProductIndex.class);

        ProductSearchService service = new ProductSearchService(List.of(
                new SqlLikeProductSearchEngine(productRepository),
                new InMemoryProductSearchEngine(productIndex),
                new PostgresFullTextSearchEngine(),
                new OpenSearchProductSearchEngine()
        ));

        Product expected = product(1L, "Shoe rack");
        when(productRepository.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                "shoe",
                "shoe",
                PageRequest.of(0, 2)
        )).thenReturn(List.of(expected));

        var result = service.search("sql_like", "shoe", 2);

        assertThat(result.supported()).isTrue();
        assertThat(result.count()).isEqualTo(1);
        assertThat(result.products()).containsExactly(expected);
    }

    @Test
    void compareIncludesAllPlannedEngines() {
        ProductRepository productRepository = mock(ProductRepository.class);
        ProductIndex productIndex = mock(ProductIndex.class);

        ProductSearchService service = new ProductSearchService(List.of(
                new SqlLikeProductSearchEngine(productRepository),
                new InMemoryProductSearchEngine(productIndex),
                new PostgresFullTextSearchEngine(),
                new OpenSearchProductSearchEngine()
        ));

        var response = service.compare("shoe", 5);

        assertThat(response.results()).hasSize(4);
        assertThat(response.results()).anyMatch(r -> r.engine() == SearchEngineType.IN_MEMORY && r.supported());
        assertThat(response.results()).anyMatch(r -> r.engine() == SearchEngineType.SQL_LIKE && r.supported());
        assertThat(response.results()).anyMatch(r -> r.engine() == SearchEngineType.POSTGRES_FTS && !r.supported());
        assertThat(response.results()).anyMatch(r -> r.engine() == SearchEngineType.OPENSEARCH && !r.supported());
    }

    @Test
    void invalidEngineNameIsRejected() {
        ProductRepository productRepository = mock(ProductRepository.class);
        ProductIndex productIndex = mock(ProductIndex.class);

        ProductSearchService service = new ProductSearchService(List.of(
                new SqlLikeProductSearchEngine(productRepository),
                new InMemoryProductSearchEngine(productIndex),
                new PostgresFullTextSearchEngine(),
                new OpenSearchProductSearchEngine()
        ));

        assertThrows(IllegalArgumentException.class, () -> service.search("bad-engine", "shoe", 10));
    }

    private Product product(Long id, String name) {
        Product product = new Product();
        product.setProductId(id);
        product.setName(name);
        return product;
    }
}
