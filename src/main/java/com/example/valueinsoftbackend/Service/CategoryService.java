package com.example.valueinsoftbackend.Service;

import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosCategory;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.MainMajor;
import com.example.valueinsoftbackend.Model.Request.SaveCategoryRequest;
import com.example.valueinsoftbackend.util.CustomPair;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

@Service
@Slf4j
public class CategoryService {

    private final DbPosCategory dbPosCategory;
    private final ObjectMapper objectMapper;

    public CategoryService(DbPosCategory dbPosCategory, ObjectMapper objectMapper) {
        this.dbPosCategory = dbPosCategory;
        this.objectMapper = objectMapper;
    }

    public ResponseEntity<String> saveCategory(int companyId, int branchId, SaveCategoryRequest request) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        if (request == null || request.getCategoryData() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_PAYLOAD_REQUIRED", "payload is required");
        }

        String payload = serializeCategoryJson(request.getCategoryData());
        int rows = dbPosCategory.saveCategoryJson(companyId, branchId, payload);
        if (rows != 1) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CATEGORY_SAVE_FAILED", "the Category not added by error!");
        }

        log.info("Saved category payload for company {} branch {}", companyId, branchId);
        return ResponseEntity.status(HttpStatus.CREATED).body("the Category added ");
    }

    public ArrayList<CustomPair> getCategoriesJson(int companyId, int branchId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        return parseCategoryPairs(dbPosCategory.getCategoryJson(branchId, companyId));
    }

    public String getCategoriesJsonFlat(int companyId, int branchId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        String payload = dbPosCategory.getCategoryJson(branchId, companyId);
        return payload == null ? "" : payload;
    }

    public ArrayList<MainMajor> getMainCategories(int companyId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        return dbPosCategory.getMainMajors(companyId);
    }

    private String serializeCategoryJson(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_INVALID_JSON", "Category payload must be valid JSON");
        }
    }

    private ArrayList<CustomPair> parseCategoryPairs(String rawPayload) {
        ArrayList<CustomPair> customPairs = new ArrayList<>();
        if (rawPayload == null || rawPayload.isBlank()) {
            return customPairs;
        }

        JsonNode node = normalizeCategoryNode(rawPayload.trim());
        if (node == null || node.isNull()) {
            return customPairs;
        }
        if (!node.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_PAYLOAD_UNSUPPORTED", "Category payload must be a JSON object");
        }

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            customPairs.add(new CustomPair(entry.getKey(), toStringList(entry.getValue())));
        }
        return customPairs;
    }

    private JsonNode normalizeCategoryNode(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (node.isTextual()) {
                return objectMapper.readTree(node.asText());
            }
            if (node.isArray()) {
                if (node.size() == 0) {
                    return objectMapper.createObjectNode();
                }
                JsonNode firstNode = node.get(0);
                if (node.size() == 1 && firstNode.isTextual()) {
                    return objectMapper.readTree(firstNode.asText());
                }
                if (node.size() == 1 && firstNode.isObject()) {
                    return firstNode;
                }
            }
            return node;
        } catch (JsonProcessingException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_PAYLOAD_UNSUPPORTED", "Category payload could not be parsed");
        }
    }

    private ArrayList<String> toStringList(JsonNode valueNode) {
        ArrayList<String> values = new ArrayList<>();
        if (valueNode == null || valueNode.isNull()) {
            return values;
        }

        if (valueNode.isArray()) {
            for (JsonNode entry : valueNode) {
                values.add(entry.asText().trim());
            }
            return values;
        }

        String text = valueNode.asText();
        if (text == null || text.isBlank()) {
            return values;
        }

        String normalized = text.trim().replace("[", "").replace("]", "");
        if (normalized.isBlank()) {
            return values;
        }

        for (String part : normalized.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }
}
