package com.example.valueinsoftbackend.Service.Public;

import com.example.valueinsoftbackend.DatabaseRequests.Public.DbPublicCatalog;
import com.example.valueinsoftbackend.Model.Public.PublicProductDTO;
import com.example.valueinsoftbackend.Model.Public.PublicTenantDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PublicCatalogService {

    private final DbPublicCatalog dbPublicCatalog;

    public PublicCatalogService(DbPublicCatalog dbPublicCatalog) {
        this.dbPublicCatalog = dbPublicCatalog;
    }

    public PublicTenantDTO getTenantInfo(String tenantCode) {
        return dbPublicCatalog.getPublicTenantByCode(tenantCode);
    }
    
    public PublicTenantDTO getTenantInfoById(int tenantId) {
        return dbPublicCatalog.getPublicTenantById(tenantId);
    }
    
    public void updateTenantInfo(int tenantId, PublicTenantDTO dto) {
        dbPublicCatalog.upsertPublicTenant(tenantId, dto);
    }

    public List<PublicProductDTO> getProducts(String tenantCode, String category, String search) {
        Integer tenantId = dbPublicCatalog.resolveTenantIdByCode(tenantCode);
        if (tenantId == null) return List.of();
        return dbPublicCatalog.getPublicProducts(tenantId, category, search);
    }

    public PublicProductDTO getProduct(String tenantCode, int productId) {
        Integer tenantId = dbPublicCatalog.resolveTenantIdByCode(tenantCode);
        if (tenantId == null) return null;
        return dbPublicCatalog.getPublicProductById(tenantId, productId);
    }

    public List<String> getCategories(String tenantCode) {
        Integer tenantId = dbPublicCatalog.resolveTenantIdByCode(tenantCode);
        if (tenantId == null) return List.of();
        return dbPublicCatalog.getPublicCategories(tenantId);
    }
}
