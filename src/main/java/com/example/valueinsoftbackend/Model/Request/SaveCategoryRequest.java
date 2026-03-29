package com.example.valueinsoftbackend.Model.Request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotNull;

public class SaveCategoryRequest {

    @NotNull(message = "category payload is required")
    private JsonNode categoryData;

    public SaveCategoryRequest() {
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public SaveCategoryRequest(JsonNode categoryData) {
        this.categoryData = categoryData;
    }

    public JsonNode getCategoryData() {
        return categoryData;
    }

    public void setCategoryData(JsonNode categoryData) {
        this.categoryData = categoryData;
    }

    @AssertTrue(message = "category payload must not be empty")
    public boolean isCategoryPayloadPresent() {
        if (categoryData == null || categoryData.isNull() || categoryData.isMissingNode()) {
            return false;
        }
        return !categoryData.isTextual() || !categoryData.asText().isBlank();
    }
}
