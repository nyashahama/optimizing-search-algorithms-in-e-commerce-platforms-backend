package com.nyasha.store.repositories;

import com.nyasha.store.entities.Category;
import com.nyasha.store.entities.Product;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findAllLoadsCategoriesForIndexRebuild() {
        Category category = new Category();
        category.setName("Accessories");
        entityManager.persist(category);

        Product product = new Product();
        product.setName("Wireless Mouse");
        product.setSku("WM-001");
        product.setDescription("Wireless ergonomic mouse");
        product.setBasePrice(29.99);
        product.getCategories().add(category);
        entityManager.persist(product);
        entityManager.flush();
        entityManager.clear();

        List<Product> products = productRepository.findAll();

        assertThat(products).hasSize(1);
        assertThat(Hibernate.isInitialized(products.getFirst().getCategories())).isTrue();
        assertThat(products.getFirst().getCategories()).extracting(Category::getName)
                .containsExactly("Accessories");
    }

    @Test
    void productHashCodeDoesNotInitializeLazyAssociationsDuringIndexing() {
        Product product = persistedProductWithCategory();

        assertThat(Hibernate.isInitialized(product.getVariants())).isFalse();
        assertThat(Hibernate.isInitialized(product.getReviews())).isFalse();

        product.hashCode();

        assertThat(Hibernate.isInitialized(product.getVariants())).isFalse();
        assertThat(Hibernate.isInitialized(product.getReviews())).isFalse();
    }

    private Product persistedProductWithCategory() {
        Category category = new Category();
        category.setName("Accessories");
        entityManager.persist(category);

        Product product = new Product();
        product.setName("Wireless Mouse");
        product.setSku("WM-001");
        product.setDescription("Wireless ergonomic mouse");
        product.setBasePrice(29.99);
        product.getCategories().add(category);
        entityManager.persist(product);
        entityManager.flush();
        entityManager.clear();

        return productRepository.findAll().getFirst();
    }
}
