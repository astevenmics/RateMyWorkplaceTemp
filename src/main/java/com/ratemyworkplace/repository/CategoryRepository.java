package com.ratemyworkplace.repository;

import com.ratemyworkplace.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByNameIgnoreCase(String name);
    Optional<Category> findBySlug(String slug);
    boolean existsByNameIgnoreCase(String name);
}
