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

    public BootstrapDataService(BootstrapVersionRepository versionRepo,
                                BootstrapDataRepository dataRepo,
                                OfflinePosProperties props,
                                AuditLogService auditLogService) {
        this.versionRepo = versionRepo;
        this.dataRepo = dataRepo;
        this.props = props;
        this.auditLogService = auditLogService;
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

        BootstrapPage<?> page = switch (resolvedType) {
            case PRODUCTS -> dataRepo.findProducts(companyId, branchId, afterId, pageSize);
            case PRICES -> dataRepo.findPrices(companyId, branchId, afterId, pageSize);
            case PAYMENT_METHODS -> staticPage(defaultPaymentMethods());
            case POS_SETTINGS -> staticPage(defaultPosSettings());
            case CASHIER_PERMISSIONS -> staticPage(defaultCashierPermissions());
            case TAXES -> staticPage(defaultTaxes());
            case DISCOUNTS -> staticPage(defaultDiscounts());
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

    private List<OfflineBootstrapPaymentMethodItem> defaultPaymentMethods() {
        return List.of(
                new OfflineBootstrapPaymentMethodItem("CASH", "Cash", true, false),
                new OfflineBootstrapPaymentMethodItem("CARD", "Card", true, true)
        );
    }

    private List<OfflineBootstrapPosSettingItem> defaultPosSettings() {
        return List.of(
                new OfflineBootstrapPosSettingItem("allowOfflineSales", true),
                new OfflineBootstrapPosSettingItem("stockPolicy", "WARN")
        );
    }

    private List<OfflineBootstrapCashierPermissionItem> defaultCashierPermissions() {
        return List.of(
                new OfflineBootstrapCashierPermissionItem("pos.offline.sync", true),
                new OfflineBootstrapCashierPermissionItem("pos.offline.retry", false)
        );
    }

    private List<OfflineBootstrapTaxItem> defaultTaxes() {
        return Collections.emptyList();
    }

    private List<OfflineBootstrapDiscountItem> defaultDiscounts() {
        return Collections.emptyList();
    }
}
