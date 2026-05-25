package com.nyasha.store.controllers;

import com.nyasha.store.entities.Category;
import com.nyasha.store.services.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategoryControllerTest {

    private final CategoryService categoryService = mock(CategoryService.class);
    private final CategoryController categoryController = new CategoryController(categoryService);

    @Test
    void categoryRoutesDelegateAndHonorStatus() {
        Category category = category(1L, "Hardware");
        when(categoryService.createCategory(category)).thenReturn(category);
        when(categoryService.getAllCategories()).thenReturn(List.of(category));
        when(categoryService.searchByName("hardware")).thenReturn(List.of(category));
        when(categoryService.getCategoryById(1L)).thenReturn(Optional.of(category));
        when(categoryService.updateCategory(1L, category)).thenReturn(category);

        ResponseEntity<Category> create = categoryController.createCategory(category);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(create.getBody()).isSameAs(category);
        assertThat(categoryController.getAllCategories(null)).containsExactly(category);
        assertThat(categoryController.getAllCategories("hardware")).containsExactly(category);
        assertThat(categoryController.getCategoryById(1L).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(categoryController.updateCategory(1L, category)).isSameAs(category);
        assertThat(categoryController.deleteCategory(1L).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void missingCategoryReturnsNotFound() {
        when(categoryService.getCategoryById(99L)).thenReturn(Optional.empty());

        ResponseEntity<Category> response = categoryController.getCategoryById(99L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
    }

    private Category category(Long id, String name) {
        Category category = new Category();
        category.setCategoryId(id);
        category.setName(name);
        return category;
    }
}
