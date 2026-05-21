package com.nyasha.store.services;

import com.nyasha.store.entities.Category;
import com.nyasha.store.repositories.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Category createCategory(Category category) {
        if (category == null || category.getName() == null || category.getName().isBlank()) {
            throw new RuntimeException("Category name is required");
        }
        return categoryRepository.save(category);
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public Optional<Category> getCategoryById(Long id) {
        return categoryRepository.findById(id);
    }

    public List<Category> searchByName(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        return categoryRepository.findByNameContainingIgnoreCase(name);
    }

    public Category updateCategory(Long id, Category categoryDetails) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        if (categoryDetails == null || categoryDetails.getName() == null || categoryDetails.getName().isBlank()) {
            throw new RuntimeException("Category name is required");
        }

        category.setName(categoryDetails.getName());
        category.setParentCategory(categoryDetails.getParentCategory());
        category.setSubCategories(categoryDetails.getSubCategories() != null ? new HashSet<>(categoryDetails.getSubCategories()) : category.getSubCategories());
        return categoryRepository.save(category);
    }

    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }
}
