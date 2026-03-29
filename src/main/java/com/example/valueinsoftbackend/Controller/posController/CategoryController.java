package com.example.valueinsoftbackend.Controller.posController;

import com.example.valueinsoftbackend.Model.MainMajor;
import com.example.valueinsoftbackend.Service.CategoryService;
import com.example.valueinsoftbackend.util.CustomPair;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Positive;
import java.util.ArrayList;

@RestController
@Validated
@RequestMapping("/Categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping("/{companyId}/{branchId}/saveCategory")
    ResponseEntity<String> newCategory(
            @RequestBody String payload,
            @PathVariable @Positive int branchId,
            @PathVariable @Positive int companyId
    ) {
        return categoryService.saveCategory(companyId, branchId, payload);
    }

    @GetMapping("/getCategoryJson/{companyId}/{branchId}")
    public ArrayList<CustomPair> getCategoriesJson(
            @PathVariable @Positive int branchId,
            @PathVariable @Positive int companyId
    ) {
        return categoryService.getCategoriesJson(companyId, branchId);
    }

    @GetMapping("/getCategoryJsonFlat/{companyId}/{branchId}")
    public String getCategoriesJsonObjectsString(
            @PathVariable @Positive int branchId,
            @PathVariable @Positive int companyId
    ) {
        return categoryService.getCategoriesJsonFlat(companyId, branchId);
    }

    @GetMapping("/getMainMajors/{companyId}")
    public ArrayList<MainMajor> getMainCategories(@PathVariable @Positive int companyId) {
        return categoryService.getMainCategories(companyId);
    }
}
