package com.ratemyworkplace.service;

import com.ratemyworkplace.domain.Category;
import com.ratemyworkplace.repository.CategoryRepository;
import com.ratemyworkplace.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<Category> all() {
        return categoryRepository.findAll(org.springframework.data.domain.Sort.by("name"));
    }

    @Transactional
    public Category create(String name) {
        String trimmed = name.trim();
        if (categoryRepository.existsByNameIgnoreCase(trimmed)) {
            throw ApiException.conflict("Category already exists");
        }
        return categoryRepository.save(new Category(trimmed, slugify(trimmed)));
    }

    /** Finds an existing category (case-insensitive) or creates it on the fly. */
    @Transactional
    public Category findOrCreate(String name) {
        String trimmed = name.trim();
        return categoryRepository.findByNameIgnoreCase(trimmed)
                .orElseGet(() -> categoryRepository.save(new Category(trimmed, slugify(trimmed))));
    }

    @Transactional
    public void delete(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw ApiException.notFound("Category not found");
        }
        categoryRepository.deleteById(id);
    }

    public static String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "category" : normalized;
    }
}
