package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.config.OfflinePosProperties;
import com.example.valueinsoftbackend.pos.offline.dto.response.BootstrapPage;
import com.example.valueinsoftbackend.pos.offline.dto.response.BootstrapDataResponse;
import com.example.valueinsoftbackend.pos.offline.dto.response.OfflineBootstrapCashierPermissionItem;
import com.example.valueinsoftbackend.pos.offline.dto.response.OfflineBootstrapDiscountItem;
import com.example.valueinsoftbackend.pos.offline.dto.response.OfflineBootstrapPaymentMethodItem;
import com.example.valueinsoftbackend.pos.offline.dto.response.OfflineBootstrapPosSettingItem;
import com.example.valueinsoftbackend.pos.offline.dto.response.OfflineBootstrapTaxItem;
import com.example.valueinsoftbackend.pos.offline.enums.BootstrapDataType;
import com.example.valueinsoftbackend.pos.offline.model.BootstrapVersionModel;
import com.example.valueinsoftbackend.pos.offline.repository.BootstrapDataRepository;
import com.example.valueinsoftbackend.pos.offline.repository.BootstrapVersionRepository;
import com.example.valueinsoftbackend.pos.offline.exception.OfflineSyncException;
import com.example.valueinsoftbackend.Service.AuthenticatedEffectiveConfigurationService;
import com.example.valueinsoftbackend.Service.LegacyInventoryBackfillService;
import com.example.valueinsoftbackend.Model.Configuration.ResolvedCapabilityConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class BootstrapDataService {

    private final BootstrapVersionRepository versionRepo;
    private final BootstrapDataRepository dataRepo;
    private final OfflinePosProperties props;
    private final AuditLogService auditLogService;
    private final AuthenticatedEffectiveConfigurationService authConfigService;
    private final LegacyInventoryBackfillService legacyInventoryBackfillService;

    public BootstrapDataService(BootstrapVersionRepository versionRepo,
                                BootstrapDataRepository dataRepo,
                                OfflinePosProperties props,
                                AuditLogService auditLogService,
                                AuthenticatedEffectiveConfigurationService authConfigService,
                                LegacyInventoryBackfillService legacyInventoryBackfillService) {
        this.versionRepo = versionRepo;
        this.dataRepo = dataRepo;
        this.props = props;
        this.auditLogService = auditLogService;
        this.authConfigService = authConfigService;
        this.legacyInventoryBackfillService = legacyInventoryBackfillService;
    }

    /**
     * Retrieves bootstrap data for a specific data type (products, prices, etc.)
     * that POS devices cache for offline use.
     */
    public BootstrapDataResponse getBootstrapData(Long companyId, Long branchId,
                                                   String dataType, Long versionNo,
                                                   String cursor, Integer size,
                                                   String principalName) {
        int pageSize = Math.min(
                size != null ? size : props.getMaxBootstrapPageSize(),
                props.getMaxBootstrapPageSize());
        pageSize = Math.max(1, pageSize);

        BootstrapDataType resolvedType = resolveDataType(dataType);
        long afterId = parseCursor(cursor);

        Optional<BootstrapVersionModel> currentVersion = versionRepo.findVersion(
                companyId, branchId, resolvedType.name());

        long latestVersion = currentVersion.map(BootstrapVersionModel::versionNo).orElse(0L);
        String checksum = currentVersion.map(BootstrapVersionModel::checksum).orElse(null);
        Instant versionLastUpdatedAt = currentVersion
                .map(BootstrapVersionModel::lastChangedAt)
                .orElse(null);

        // If client already has the latest version, return empty
        if (versionNo != null && versionNo >= latestVersion && afterId == 0) {
            log.debug("Client already up to date: dataType={}, clientVersion={}, latestVersion={}",
                    resolvedType, versionNo, latestVersion);
            return new BootstrapDataResponse(
                    companyId, branchId, resolvedType.name(), latestVersion, checksum, versionLastUpdatedAt,
                    Instant.now(), Collections.emptyList(), false, null);
        }

        if (resolvedType == BootstrapDataType.PRODUCTS || resolvedType == BootstrapDataType.PRICES) {
            legacyInventoryBackfillService.backfillBranchProducts(companyId.intValue(), branchId.intValue());
        }

        BootstrapPage<?> page = switch (resolvedType) {
            case PRODUCTS -> dataRepo.findProducts(companyId, branchId, afterId, pageSize);
            case PRICES -> dataRepo.findPrices(companyId, branchId, afterId, pageSize);
            case PAYMENT_METHODS -> staticPage(dataRepo.findPaymentMethods(companyId, branchId));
            case POS_SETTINGS -> staticPage(posSettings(companyId, branchId));
            case CASHIER_PERMISSIONS -> staticPage(fetchRealCashierPermissions(principalName, companyId, branchId));
            case TAXES -> staticPage(dataRepo.findTaxes(companyId));
            case DISCOUNTS -> staticPage(dataRepo.findDiscounts(companyId, branchId));
        };

        log.info("Bootstrap data request: companyId={}, branchId={}, dataType={}, pageSize={}",
                companyId, branchId, resolvedType, pageSize);
        auditLogService.logSyncEvent(
                companyId, branchId,
                null, null, null, null,
                "BOOTSTRAP_DATA_VIEWED",
                "Bootstrap data viewed by " + principalName + " for type " + resolvedType,
                null);

        return new BootstrapDataResponse(
                companyId, branchId, resolvedType.name(), latestVersion, checksum,
                page.lastUpdatedAt() != null ? page.lastUpdatedAt() : versionLastUpdatedAt,
                Instant.now(), new ArrayList<>(page.items()), page.hasMore(), page.nextCursor());
    }

    private BootstrapDataType resolveDataType(String dataType) {
        try {
            return BootstrapDataType.valueOf(dataType == null ? "" : dataType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new OfflineSyncException("UNSUPPORTED_BOOTSTRAP_DATA_TYPE",
                    "Unsupported bootstrap data type: " + dataType);
        }
    }

    private long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0L;
        }
        try {
            long parsed = Long.parseLong(cursor);
            if (parsed < 0) {
                throw new NumberFormatException("cursor must be non-negative");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new OfflineSyncException("INVALID_BOOTSTRAP_CURSOR", "Invalid bootstrap cursor");
        }
    }

    private BootstrapPage<?> staticPage(List<?> items) {
        return new BootstrapPage<>(items, false, null, Instant.now());
    }

    private List<OfflineBootstrapPosSettingItem> posSettings(Long companyId, Long branchId) {
        List<OfflineBootstrapPosSettingItem> items = new ArrayList<>();
        items.add(new OfflineBootstrapPosSettingItem("allowOfflineSales", props.isAllowOfflineSync()));
        items.add(new OfflineBootstrapPosSettingItem("stockPolicy", "WARN"));
        items.add(new OfflineBootstrapPosSettingItem("maxOrdersPerBatch", props.getMaxOrdersPerBatch()));
        items.add(new OfflineBootstrapPosSettingItem("maxItemsPerOrder", props.getMaxItemsPerOrder()));
        items.add(new OfflineBootstrapPosSettingItem("maxOfflineHoursDefault", props.getMaxOfflineHoursDefault()));
        items.addAll(dataRepo.findPosSettings(companyId, branchId));
        return items;
    }

    private List<OfflineBootstrapCashierPermissionItem> fetchRealCashierPermissions(String principalName, Long companyId, Long branchId) {
        try {
            ArrayList<ResolvedCapabilityConfig> configs = authConfigService.getEffectiveCapabilitiesForAuthenticatedUser(
                    principalName, companyId.intValue(), branchId.intValue());

            return configs.stream()
                    .filter(c -> c.getCapabilityKey().startsWith("pos."))
                    .map(c -> new OfflineBootstrapCashierPermissionItem(
                            c.getCapabilityKey(),
                            "allow".equalsIgnoreCase(c.getGrantMode())))
                    .toList();
        } catch (Exception e) {
            log.error("Failed to fetch real cashier permissions for {}: {}", principalName, e.getMessage());
            return List.of(
                    new OfflineBootstrapCashierPermissionItem("pos.offline.sync", true),
                    new OfflineBootstrapCashierPermissionItem("pos.offline.retry", false)
            );
        }
    }

}
