package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.config.OfflinePosProperties;
import com.example.valueinsoftbackend.pos.offline.dto.response.BootstrapDataResponse;
import com.example.valueinsoftbackend.pos.offline.model.BootstrapVersionModel;
import com.example.valueinsoftbackend.pos.offline.repository.BootstrapVersionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

@Service
@Slf4j
public class BootstrapDataService {

    private final BootstrapVersionRepository versionRepo;
    private final OfflinePosProperties props;

    public BootstrapDataService(BootstrapVersionRepository versionRepo, OfflinePosProperties props) {
        this.versionRepo = versionRepo;
        this.props = props;
    }

    /**
     * Retrieves bootstrap data for a specific data type (products, prices, etc.)
     * that POS devices cache for offline use.
     */
    public BootstrapDataResponse getBootstrapData(Long companyId, Long branchId,
                                                   String dataType, Long versionNo,
                                                   String cursor, Integer size) {
        int pageSize = Math.min(
                size != null ? size : props.getMaxBootstrapPageSize(),
                props.getMaxBootstrapPageSize());

        Optional<BootstrapVersionModel> currentVersion = versionRepo.findVersion(
                companyId, branchId, dataType);

        long latestVersion = currentVersion.map(BootstrapVersionModel::versionNo).orElse(0L);
        String checksum = currentVersion.map(BootstrapVersionModel::checksum).orElse(null);

        // If client already has the latest version, return empty
        if (versionNo != null && versionNo >= latestVersion) {
            log.debug("Client already up to date: dataType={}, clientVersion={}, latestVersion={}",
                    dataType, versionNo, latestVersion);
            return new BootstrapDataResponse(
                    companyId, branchId, dataType, latestVersion, checksum,
                    Instant.now(), Collections.emptyList(), false, null);
        }

        // TODO: Implement data fetching per data type:
        //   - PRODUCTS:            Query tenant's inventory_product table
        //   - PRICES:              Query tenant's pricing / price-list tables
        //   - TAXES:               Query tenant's tax configuration
        //   - DISCOUNTS:           Query tenant's active discount rules
        //   - PAYMENT_METHODS:     Query tenant's payment method configuration
        //   - CASHIER_PERMISSIONS: Query tenant's user role/permissions for POS cashiers
        //   - POS_SETTINGS:        Query tenant's branch POS settings
        //
        // Each should support cursor-based pagination using the `cursor` and `pageSize` params.

        log.info("Bootstrap data request: companyId={}, branchId={}, dataType={}, pageSize={}",
                companyId, branchId, dataType, pageSize);

        return new BootstrapDataResponse(
                companyId, branchId, dataType, latestVersion, checksum,
                Instant.now(), Collections.emptyList(), false, null);
    }
}
