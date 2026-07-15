package com.myworktea.web;

import com.myworktea.dto.DtoMapper;
import com.myworktea.dto.Responses;
import com.myworktea.service.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<Responses.CategoryDto> all() {
        return categoryService.all().stream().map(DtoMapper::category).toList();
    }
}