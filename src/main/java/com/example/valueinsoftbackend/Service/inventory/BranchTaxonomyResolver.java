package com.example.valueinsoftbackend.Service.inventory;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.ProductReceiptClassificationRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.ProductReceiptProductRequest;
import com.example.valueinsoftbackend.Service.CategoryService;
import com.example.valueinsoftbackend.util.CustomPair;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class BranchTaxonomyResolver {

    private final CategoryService categoryService;
    private final ObjectMapper objectMapper;

    public BranchTaxonomyResolver(CategoryService categoryService, ObjectMapper objectMapper) {
        this.categoryService = categoryService;
        this.objectMapper = objectMapper;
    }

    public ResolvedClassification resolveForProduct(int companyId, int branchId, ProductReceiptProductRequest product) {
        BranchTaxonomy taxonomy = loadBranchTaxonomy(companyId, branchId);
        ProductReceiptClassificationRequest classification = product.getClassification();
        if (classification != null && hasAnyKey(classification)) {
            return resolveByKeys(taxonomy, classification);
        }
        return resolveLegacyLabels(taxonomy, product);
    }

    BranchTaxonomy loadBranchTaxonomy(int companyId, int branchId) {
        String rawPayload = categoryService.getCategoriesJsonFlat(companyId, branchId);
        return normalize(rawPayload);
    }

    BranchTaxonomy normalize(String rawPayload) {
        JsonNode root = parseNode(rawPayload);
        if (root == null || root.isNull()) {
            return new BranchTaxonomy(List.of());
        }

        JsonNode canonical = unwrapCategoryContainer(root);
        if (canonical != null && canonical.isObject() && canonical.has("groups") && canonical.get("groups").isArray()) {
            return new BranchTaxonomy(parseSchemaGroups(canonical.get("groups")));
        }

        ArrayList<CustomPair> pairs = new ArrayList<>();
        appendLegacyPairs(pairs, root);
        List<LegacyGroupInput> branchGroups = parseLegacyBranchGroups(root);
        return new BranchTaxonomy(buildGroupsFromLegacy(pairs, branchGroups));
    }

    private ResolvedClassification resolveByKeys(BranchTaxonomy taxonomy, ProductReceiptClassificationRequest classification) {
        String groupKey = normalizeRequiredKey(classification.getGroupKey(), "CLASSIFICATION_GROUP_REQUIRED", "classification.groupKey is required");
        String categoryKey = normalizeRequiredKey(classification.getCategoryKey(), "CLASSIFICATION_CATEGORY_REQUIRED", "classification.categoryKey is required");
        String subcategoryKey = normalizeRequiredKey(classification.getSubcategoryKey(), "CLASSIFICATION_SUBCATEGORY_REQUIRED", "classification.subcategoryKey is required");

        TaxonomyGroup group = taxonomy.groups().stream()
                .filter(item -> groupKey.equals(item.key()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "CLASSIFICATION_GROUP_INVALID", "Selected group does not exist for this branch"));

        TaxonomyCategory category = group.categories().stream()
                .filter(item -> categoryKey.equals(item.key()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "CLASSIFICATION_CATEGORY_INVALID", "Selected category does not belong to the selected group"));

        TaxonomySubcategory subcategory = category.subcategories().stream()
                .filter(item -> subcategoryKey.equals(item.key()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "CLASSIFICATION_SUBCATEGORY_INVALID", "Selected subcategory does not belong to the selected category"));

        return new ResolvedClassification(group.key(), group.label(), category.key(), category.label(), subcategory.key(), subcategory.label(), 2);
    }

    private ResolvedClassification resolveLegacyLabels(BranchTaxonomy taxonomy, ProductReceiptProductRequest product) {
        String categoryName = firstNonBlank(product.getCategoryName(), product.getCategoryId() == null ? null : String.valueOf(product.getCategoryId()));
        String subcategoryName = product.getSubcategoryName();
        if (categoryName == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CLASSIFICATION_REQUIRED", "classification is required when creating a product");
        }

        List<ResolvedClassification> matches = new ArrayList<>();
        for (TaxonomyGroup group : taxonomy.groups()) {
            for (TaxonomyCategory category : group.categories()) {
                boolean categoryMatches = equalsLabel(category.label(), categoryName);
                boolean legacyGroupAsCategory = equalsLabel(group.label(), categoryName);
                if (!categoryMatches && !legacyGroupAsCategory) {
                    continue;
                }
                if (subcategoryName == null || subcategoryName.isBlank()) {
                    if (category.subcategories().size() == 1) {
                        TaxonomySubcategory only = category.subcategories().get(0);
                        matches.add(new ResolvedClassification(group.key(), group.label(), category.key(), category.label(), only.key(), only.label(), 1));
                    }
                    continue;
                }
                for (TaxonomySubcategory subcategory : category.subcategories()) {
                    if (equalsLabel(subcategory.label(), subcategoryName)) {
                        matches.add(new ResolvedClassification(group.key(), group.label(), category.key(), category.label(), subcategory.key(), subcategory.label(), 1));
                    }
                }
            }
        }

        if (matches.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_TEMPLATE_COMBINATION_INVALID", "Category and subcategory do not match the branch taxonomy");
        }
        if (matches.size() > 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CLASSIFICATION_AMBIGUOUS", "Legacy category labels match more than one branch taxonomy path");
        }
        return matches.get(0);
    }

    private List<TaxonomyGroup> parseSchemaGroups(JsonNode groupsNode) {
        List<TaxonomyGroup> groups = new ArrayList<>();
        Set<String> usedGroupKeys = new HashSet<>();
        int groupIndex = 0;
        for (JsonNode groupNode : groupsNode) {
            if (!groupNode.isObject()) {
                continue;
            }
            String groupLabel = firstNonBlank(text(groupNode, "label"), text(groupNode, "displayName"));
            if (groupLabel == null) {
                groupLabel = "Group " + (++groupIndex);
            }
            String groupKey = uniqueKey(firstNonBlank(text(groupNode, "key"), text(groupNode, "groupKey"), groupLabel), usedGroupKeys);
            groups.add(new TaxonomyGroup(groupKey, groupLabel, parseSchemaCategories(groupNode.get("categories"))));
        }
        return groups;
    }

    private List<TaxonomyCategory> parseSchemaCategories(JsonNode categoriesNode) {
        List<TaxonomyCategory> categories = new ArrayList<>();
        if (categoriesNode == null || !categoriesNode.isArray()) {
            return categories;
        }
        Set<String> usedCategoryKeys = new HashSet<>();
        for (JsonNode categoryNode : categoriesNode) {
            if (!categoryNode.isObject()) {
                continue;
            }
            String categoryLabel = firstNonBlank(text(categoryNode, "label"), text(categoryNode, "displayName"));
            if (categoryLabel == null) {
                continue;
            }
            String categoryKey = uniqueKey(firstNonBlank(text(categoryNode, "key"), text(categoryNode, "categoryKey"), categoryLabel), usedCategoryKeys);
            categories.add(new TaxonomyCategory(categoryKey, categoryLabel, parseSchemaSubcategories(categoryNode.get("subcategories"))));
        }
        return categories;
    }

    private List<TaxonomySubcategory> parseSchemaSubcategories(JsonNode subcategoriesNode) {
        List<TaxonomySubcategory> subcategories = new ArrayList<>();
        if (subcategoriesNode == null || !subcategoriesNode.isArray()) {
            return subcategories;
        }
        Set<String> usedSubcategoryKeys = new HashSet<>();
        for (JsonNode subcategoryNode : subcategoriesNode) {
            String subcategoryLabel = subcategoryNode.isTextual()
                    ? subcategoryNode.asText()
                    : firstNonBlank(text(subcategoryNode, "label"), text(subcategoryNode, "displayName"));
            if (subcategoryLabel == null || subcategoryLabel.isBlank()) {
                continue;
            }
            String requestedKey = subcategoryNode.isObject()
                    ? firstNonBlank(text(subcategoryNode, "key"), text(subcategoryNode, "subcategoryKey"), subcategoryLabel)
                    : subcategoryLabel;
            subcategories.add(new TaxonomySubcategory(uniqueKey(requestedKey, usedSubcategoryKeys), subcategoryLabel.trim()));
        }
        return subcategories;
    }

    private List<TaxonomyGroup> buildGroupsFromLegacy(List<CustomPair> pairs, List<LegacyGroupInput> branchGroups) {
        Map<String, CustomPair> categoryByLabel = new LinkedHashMap<>();
        for (CustomPair pair : pairs) {
            if (pair.getKey() != null && !pair.getKey().trim().isEmpty()) {
                categoryByLabel.put(pair.getKey().trim().toLowerCase(Locale.ROOT), pair);
            }
        }

        Set<String> usedGroupKeys = new HashSet<>();
        Set<String> assignedCategories = new HashSet<>();
        List<TaxonomyGroup> groups = new ArrayList<>();
        if (!branchGroups.isEmpty()) {
            for (int index = 0; index < branchGroups.size(); index++) {
                LegacyGroupInput input = branchGroups.get(index);
                Set<String> usedCategoryKeys = new HashSet<>();
                List<TaxonomyCategory> categories = new ArrayList<>();
                for (String categoryLabel : input.categories()) {
                    CustomPair pair = categoryByLabel.get(categoryLabel.toLowerCase(Locale.ROOT));
                    if (pair != null) {
                        categories.add(toTaxonomyCategory(pair, usedCategoryKeys));
                        assignedCategories.add(pair.getKey().trim().toLowerCase(Locale.ROOT));
                    }
                }
                String groupLabel = firstNonBlank(input.label(), "Group " + (index + 1));
                groups.add(new TaxonomyGroup(uniqueKey(groupLabel, usedGroupKeys), groupLabel, categories));
            }
        } else {
            Set<String> usedCategoryKeys = new HashSet<>();
            List<TaxonomyCategory> categories = new ArrayList<>();
            for (CustomPair pair : pairs) {
                categories.add(toTaxonomyCategory(pair, usedCategoryKeys));
                assignedCategories.add(pair.getKey().trim().toLowerCase(Locale.ROOT));
            }
            groups.add(new TaxonomyGroup(uniqueKey("default", usedGroupKeys), "Default", categories));
        }

        List<TaxonomyCategory> uncategorized = new ArrayList<>();
        Set<String> usedUncategorizedCategoryKeys = new HashSet<>();
        for (CustomPair pair : pairs) {
            if (pair.getKey() != null && !assignedCategories.contains(pair.getKey().trim().toLowerCase(Locale.ROOT))) {
                uncategorized.add(toTaxonomyCategory(pair, usedUncategorizedCategoryKeys));
            }
        }
        if (!uncategorized.isEmpty()) {
            groups.add(new TaxonomyGroup(uniqueKey("uncategorized", usedGroupKeys), "Uncategorized", uncategorized));
        }
        return groups;
    }

    private TaxonomyCategory toTaxonomyCategory(CustomPair pair, Set<String> usedCategoryKeys) {
        String categoryLabel = pair.getKey().trim();
        Set<String> usedSubcategoryKeys = new HashSet<>();
        List<TaxonomySubcategory> subcategories = new ArrayList<>();
        if (pair.getValue() instanceof List<?> values) {
            for (Object value : values) {
                String subcategoryLabel = value == null ? null : value.toString().trim();
                if (subcategoryLabel != null && !subcategoryLabel.isEmpty()) {
                    subcategories.add(new TaxonomySubcategory(uniqueKey(subcategoryLabel, usedSubcategoryKeys), subcategoryLabel));
                }
            }
        }
        return new TaxonomyCategory(uniqueKey(categoryLabel, usedCategoryKeys), categoryLabel, subcategories);
    }

    private List<LegacyGroupInput> parseLegacyBranchGroups(JsonNode node) {
        JsonNode branchGroupsNode = findField(node, "branchGroups");
        if (branchGroupsNode == null || !branchGroupsNode.isArray()) {
            return List.of();
        }
        List<LegacyGroupInput> result = new ArrayList<>();
        for (JsonNode groupNode : branchGroupsNode) {
            if (!groupNode.isObject()) {
                continue;
            }
            String label = firstNonBlank(text(groupNode, "label"), text(groupNode, "key"), text(groupNode, "displayName"));
            List<String> categories = new ArrayList<>();
            JsonNode categoriesNode = groupNode.get("categories");
            if (categoriesNode != null && categoriesNode.isArray()) {
                for (JsonNode categoryNode : categoriesNode) {
                    String categoryLabel = categoryNode.isTextual()
                            ? categoryNode.asText()
                            : firstNonBlank(text(categoryNode, "label"), text(categoryNode, "key"), text(categoryNode, "displayName"));
                    if (categoryLabel != null && !categoryLabel.isBlank()) {
                        categories.add(categoryLabel.trim());
                    }
                }
            }
            if (label != null && !categories.isEmpty()) {
                result.add(new LegacyGroupInput(label, categories));
            }
        }
        return result;
    }

    private void appendLegacyPairs(ArrayList<CustomPair> pairs, JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            if (node.has("categoryData")) {
                appendLegacyPairs(pairs, node.get("categoryData"));
                return;
            }
            if (node.hasNonNull("key") && node.has("value")) {
                pairs.add(new CustomPair(node.get("key").asText(), toStringList(node.get("value"))));
                return;
            }
            node.fields().forEachRemaining(entry -> {
                if (!"groups".equals(entry.getKey()) && !"branchGroups".equals(entry.getKey())) {
                    pairs.add(new CustomPair(entry.getKey(), toStringList(entry.getValue())));
                }
            });
            return;
        }
        if (node.isArray()) {
            for (JsonNode entry : node) {
                appendLegacyPairs(pairs, entry);
            }
        }
    }

    private JsonNode parseNode(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(rawPayload);
            while (node != null && node.isTextual()) {
                node = objectMapper.readTree(node.asText());
            }
            if (node != null && node.isArray() && node.size() == 1) {
                JsonNode first = node.get(0);
                if (first.isTextual()) {
                    return parseNode(first.asText());
                }
                if (first.isObject()) {
                    return first;
                }
            }
            return node;
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_PAYLOAD_UNSUPPORTED", "Branch category payload could not be parsed");
        }
    }

    private JsonNode unwrapCategoryContainer(JsonNode node) {
        if (node == null || !node.isObject()) {
            return node;
        }
        JsonNode current = node;
        while (current != null && current.isObject() && current.has("categoryData") && current.get("categoryData").isObject()) {
            JsonNode inner = current.get("categoryData");
            if (inner.has("groups") || inner.has("categoryData")) {
                current = inner;
            } else {
                break;
            }
        }
        return current;
    }

    private JsonNode findField(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            if (node.has(fieldName)) {
                return node.get(fieldName);
            }
            if (node.has("categoryData")) {
                JsonNode found = findField(node.get("categoryData"), fieldName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private ArrayList<String> toStringList(JsonNode valueNode) {
        ArrayList<String> values = new ArrayList<>();
        if (valueNode == null || valueNode.isNull()) {
            return values;
        }
        if (valueNode.isArray()) {
            for (JsonNode value : valueNode) {
                if (value != null && !value.asText().isBlank()) {
                    values.add(value.asText().trim());
                }
            }
            return values;
        }
        String text = valueNode.asText();
        if (text == null || text.isBlank()) {
            return values;
        }
        for (String part : text.replace("[", "").replace("]", "").split(",")) {
            if (!part.trim().isEmpty()) {
                values.add(part.trim());
            }
        }
        return values;
    }

    private boolean hasAnyKey(ProductReceiptClassificationRequest classification) {
        return classification.getGroupKey() != null && !classification.getGroupKey().isBlank()
                || classification.getCategoryKey() != null && !classification.getCategoryKey().isBlank()
                || classification.getSubcategoryKey() != null && !classification.getSubcategoryKey().isBlank();
    }

    private String normalizeRequiredKey(String value, String code, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
        }
        return value.trim();
    }

    private String uniqueKey(String value, Set<String> usedKeys) {
        String base = slugify(value);
        String candidate = base;
        int suffix = 2;
        while (usedKeys.contains(candidate)) {
            candidate = base + "_" + suffix++;
        }
        usedKeys.add(candidate);
        return candidate;
    }

    private String slugify(String value) {
        String text = value == null ? "item" : value.trim().toLowerCase(Locale.ROOT);
        text = Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "");
        text = text.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return text.isBlank() ? "item" : text;
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.isObject() || !node.hasNonNull(field)) {
            return null;
        }
        String value = node.get(field).asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred.trim() : (fallback == null || fallback.isBlank() ? null : fallback.trim());
    }

    private String firstNonBlank(String first, String second, String third) {
        String value = firstNonBlank(first, second);
        return value == null ? firstNonBlank(third, null) : value;
    }

    private boolean equalsLabel(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    public record BranchTaxonomy(List<TaxonomyGroup> groups) {
    }

    public record TaxonomyGroup(String key, String label, List<TaxonomyCategory> categories) {
    }

    public record TaxonomyCategory(String key, String label, List<TaxonomySubcategory> subcategories) {
    }

    public record TaxonomySubcategory(String key, String label) {
    }

    private record LegacyGroupInput(String label, List<String> categories) {
    }

    public record ResolvedClassification(
            String groupKey,
            String groupName,
            String categoryKey,
            String categoryName,
            String subcategoryKey,
            String subcategoryName,
            int taxonomyVersion
    ) {
    }
}
