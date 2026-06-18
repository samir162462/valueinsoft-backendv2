package com.example.valueinsoftbackend.Model.Request;

import com.example.valueinsoftbackend.Model.ProductFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class ProductCatalogExportRequest {

    @Positive(message = "companyId must be positive")
    private int companyId;

    @Positive(message = "branchId must be positive")
    private int branchId;

    @Size(max = 30, message = "searchType must be 30 characters or fewer")
    private String searchType = "allData";

    @Size(max = 120, message = "text must be 120 characters or fewer")
    private String text = "";

    @Valid
    private ProductFilter productFilter;

    @Size(max = 40, message = "businessLineKey must be 40 characters or fewer")
    private String businessLineKey;

    @Size(max = 80, message = "templateKey must be 80 characters or fewer")
    private String templateKey;

    @Size(max = 20, message = "locale must be 20 characters or fewer")
    private String locale;

    @Pattern(regexp = "^(ltr|rtl)?$", message = "direction must be ltr or rtl")
    private String direction;

    public int getCompanyId() {
        return companyId;
    }

    public void setCompanyId(int companyId) {
        this.companyId = companyId;
    }

    public int getBranchId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
    }

    public String getSearchType() {
        return searchType;
    }

    public void setSearchType(String searchType) {
        this.searchType = searchType;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public ProductFilter getProductFilter() {
        return productFilter;
    }

    public void setProductFilter(ProductFilter productFilter) {
        this.productFilter = productFilter;
    }

    public String getBusinessLineKey() {
        return businessLineKey;
    }

    public void setBusinessLineKey(String businessLineKey) {
        this.businessLineKey = businessLineKey;
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public void setTemplateKey(String templateKey) {
        this.templateKey = templateKey;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }
}
