package com.example.valueinsoftbackend.Service;

import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosCategory;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.MainMajor;
import com.example.valueinsoftbackend.Model.Request.SaveCategoryRequest;
import com.example.valueinsoftbackend.Config.CacheConfig;
import com.example.valueinsoftbackend.util.CustomPair;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;

@Service
@Slf4j
public class CategoryService {

    private final DbPosCategory dbPosCategory;
    private final ObjectMapper objectMapper;
    private final BusinessPackageCatalogService businessPackageCatalogService;

    public CategoryService(DbPosCategory dbPosCategory,
                           ObjectMapper objectMapper,
                           BusinessPackageCatalogService businessPackageCatalogService) {
        this.dbPosCategory = dbPosCategory;
        this.objectMapper = objectMapper;
        this.businessPackageCatalogService = businessPackageCatalogService;
    }

    @CacheEvict(cacheNames = {
            CacheConfig.CATEGORY_JSON_FLAT,
            CacheConfig.CATEGORY_PAIRS
    }, key = "#companyId + ':' + #branchId")
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

    @Cacheable(cacheNames = CacheConfig.CATEGORY_PAIRS, key = "#companyId + ':' + #branchId")
    public ArrayList<CustomPair> getCategoriesJson(int companyId, int branchId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        businessPackageCatalogService.provisionBranchCategoriesIfMissing(companyId, branchId);
        String payload = dbPosCategory.getCategoryJson(branchId, companyId);
        return parseCategoryPairs(injectMissingProducts(companyId, branchId, payload));
    }

    @Cacheable(cacheNames = CacheConfig.CATEGORY_JSON_FLAT, key = "#companyId + ':' + #branchId")
    public String getCategoriesJsonFlat(int companyId, int branchId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        businessPackageCatalogService.provisionBranchCategoriesIfMissing(companyId, branchId);
        String payload = dbPosCategory.getCategoryJson(branchId, companyId);
        return injectMissingProducts(companyId, branchId, payload);
    }

    @Cacheable(cacheNames = CacheConfig.MAIN_MAJORS, key = "#companyId")
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
        appendCategoryPairs(customPairs, node);
        if (customPairs.isEmpty() && !node.isObject() && !node.isArray()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_PAYLOAD_UNSUPPORTED", "Category payload must be a JSON object or array of category objects");
        }
        return customPairs;
    }

    private void appendCategoryPairs(ArrayList<CustomPair> customPairs, JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }

        if (node.isObject()) {
            if (node.has("categoryData")) {
                appendCategoryPairs(customPairs, node.get("categoryData"));
                return;
            }

            if (node.hasNonNull("key") && node.has("value")) {
                customPairs.add(new CustomPair(node.get("key").asText(), toStringList(node.get("value"))));
                return;
            }

            node.fields().forEachRemaining(entry -> {
                if ("branchGroups".equals(entry.getKey()) || "groups".equals(entry.getKey())) {
                    return;
                }
                if ("categoryData".equals(entry.getKey())) {
                    appendCategoryPairs(customPairs, entry.getValue());
                    return;
                }
                customPairs.add(new CustomPair(entry.getKey(), toStringList(entry.getValue())));
            });
            return;
        }

        if (node.isArray()) {
            for (JsonNode entryNode : node) {
                if (entryNode == null || entryNode.isNull()) {
                    continue;
                }

                if (entryNode.isTextual()) {
                    appendCategoryPairs(customPairs, normalizeCategoryNode(entryNode.asText()));
                    continue;
                }

                appendCategoryPairs(customPairs, entryNode);
            }
            return;
        }

        throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_PAYLOAD_UNSUPPORTED", "Category payload must be a JSON object or array of category objects");
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

    private String injectMissingProducts(int companyId, int branchId, String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            rawPayload = "{\"categoryData\":{\"categoryData\":[]}}";
        }

        try {
            JsonNode rootNode = objectMapper.readTree(rawPayload);
            ArrayList<CustomPair> existingPairs = parseCategoryPairs(rawPayload);
            java.util.Set<String> existingNames = new java.util.HashSet<>();
            for (CustomPair pair : existingPairs) {
                if (pair.getValue() instanceof java.util.List) {
                    ((java.util.List<?>) pair.getValue()).forEach(v -> existingNames.add(String.valueOf(v).trim()));
                }
            }

            Map<String, java.util.List<String>> allProducts = dbPosCategory.getActiveProductsGroupedByBusinessLine(companyId, branchId);
            Map<String, java.util.List<String>> toInject = new java.util.HashMap<>();
            for (Map.Entry<String, java.util.List<String>> entry : allProducts.entrySet()) {
                String blKey = entry.getKey();
                for (String prodName : entry.getValue()) {
                    if (!existingNames.contains(prodName)) {
                        String targetCategory = findBestCategory(prodName, blKey, existingPairs, rootNode);
                        toInject.computeIfAbsent(targetCategory, k -> new ArrayList<>()).add(prodName);
                    }
                }
            }

            if (!toInject.isEmpty()) {
                com.fasterxml.jackson.databind.node.ObjectNode rootObj = (com.fasterxml.jackson.databind.node.ObjectNode) rootNode;
                com.fasterxml.jackson.databind.node.ArrayNode catArray = null;

                if (rootObj.has("categoryData") && rootObj.get("categoryData").isObject() && rootObj.get("categoryData").has("categoryData")) {
                    catArray = (com.fasterxml.jackson.databind.node.ArrayNode) rootObj.get("categoryData").get("categoryData");
                } else if (rootObj.has("categoryData") && rootObj.get("categoryData").isArray()) {
                    catArray = (com.fasterxml.jackson.databind.node.ArrayNode) rootObj.get("categoryData");
                } else {
                    com.fasterxml.jackson.databind.node.ObjectNode inner = objectMapper.createObjectNode();
                    catArray = objectMapper.createArrayNode();
                    inner.set("categoryData", catArray);
                    rootObj.set("categoryData", inner);
                }

                for (Map.Entry<String, java.util.List<String>> entry : toInject.entrySet()) {
                    String catName = entry.getKey();
                    com.fasterxml.jackson.databind.node.ObjectNode targetCat = null;
                    for (JsonNode n : catArray) {
                        if (n.has("key") && n.get("key").asText().equals(catName)) {
                            targetCat = (com.fasterxml.jackson.databind.node.ObjectNode) n;
                            break;
                        }
                    }

                    if (targetCat == null) {
                        targetCat = objectMapper.createObjectNode();
                        targetCat.put("key", catName);
                        targetCat.set("value", objectMapper.createArrayNode());
                        catArray.add(targetCat);
                    }

                    com.fasterxml.jackson.databind.node.ArrayNode valArray = (com.fasterxml.jackson.databind.node.ArrayNode) targetCat.get("value");
                    for (String prod : entry.getValue()) {
                        valArray.add(prod);
                    }
                }
                return objectMapper.writeValueAsString(rootObj);
            }
            return rawPayload;
        } catch (Exception ex) {
            log.error("Failed to inject missing products", ex);
            return rawPayload;
        }
    }

    private String findBestCategory(String productName, String businessLineKey, 
                                    java.util.List<CustomPair> existingPairs, JsonNode rootNode) {
        String nameLower = productName.toLowerCase();

        // 1. Direct substring match with category names
        for (CustomPair pair : existingPairs) {
            String catKey = pair.getKey();
            if (nameLower.contains(catKey.toLowerCase())) {
                return catKey;
            }
        }

        // 2. Substring match with words in existing values
        String[] words = nameLower.split("\\s+");
        for (CustomPair pair : existingPairs) {
            if (pair.getValue() instanceof java.util.List) {
                for (Object val : (java.util.List<?>) pair.getValue()) {
                    String valStr = String.valueOf(val).toLowerCase();
                    for (String word : words) {
                        if (word.length() >= 3 && valStr.contains(word)) {
                            return pair.getKey();
                        }
                    }
                }
            }
        }

        // 3. Fallback based on business_line_key and group mapping
        String targetGroup = mapBusinessLineToGroup(businessLineKey);
        if (rootNode.has("categoryData") && rootNode.get("categoryData").isObject() && 
            rootNode.get("categoryData").has("branchGroups")) {
            JsonNode groupsNode = rootNode.get("categoryData").get("branchGroups");
            if (groupsNode.isArray()) {
                for (JsonNode group : groupsNode) {
                    if (group.has("key") && group.get("key").asText().equalsIgnoreCase(targetGroup)) {
                        if (group.has("categories") && group.get("categories").isArray() && group.get("categories").size() > 0) {
                            return group.get("categories").get(0).asText();
                        }
                    }
                }
            }
        }

        // 4. Absolute fallback
        if (!existingPairs.isEmpty()) {
            return existingPairs.get(0).getKey();
        }
        return "Other";
    }

    private String mapBusinessLineToGroup(String blKey) {
        if (blKey == null) return "mobile";
        return switch (blKey) {
            case "MOBILE" -> "mobile";
            case "ACCESSORY", "SPARE_PART" -> "Ears";
            default -> "mobile";
        };
    }
}
