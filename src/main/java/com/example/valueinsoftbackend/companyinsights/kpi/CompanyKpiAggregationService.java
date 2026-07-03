package com.example.valueinsoftbackend.companyinsights.kpi;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.companyinsights.config.CompanyInsightProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Builds trusted KPI snapshots for a company. Idempotent: re-running a date overwrites
 * its snapshot rows (upsert), so the daily job can safely re-close a trailing window to
 * absorb late orders/returns.
 *
 * <p>One transaction per (company, businessDate) unit: a failure for one company/date never
 * rolls back another.
 */
@Service
@Slf4j
public class CompanyKpiAggregationService {

    private final DbBranch dbBranch;
    private final BranchKpiSourceReader branchKpiSourceReader;
    private final CompanyKpiRepository kpiRepository;
    private final CompanyInventorySnapshotRepository inventorySnapshotRepository;
    private final CompanyInsightProperties properties;

    public CompanyKpiAggregationService(DbBranch dbBranch,
                                        BranchKpiSourceReader branchKpiSourceReader,
                                        CompanyKpiRepository kpiRepository,
                                        CompanyInventorySnapshotRepository inventorySnapshotRepository,
                                        CompanyInsightProperties properties) {
        this.dbBranch = dbBranch;
        this.branchKpiSourceReader = branchKpiSourceReader;
        this.kpiRepository = kpiRepository;
        this.inventorySnapshotRepository = inventorySnapshotRepository;
        this.properties = properties;
    }

    /**
     * Aggregate a single (company, businessDate): upsert every branch row then rebuild
     * the company row from those branch rows. Returns rows written (branch rows).
     */
    @Transactional
    public int aggregateCompanyDate(int companyId, LocalDate businessDate) {
        long startedAt = System.nanoTime();
        List<Branch> branches = dbBranch.getBranchByCompanyId(companyId);
        int written = 0;
        for (Branch branch : branches) {
            BranchDailyKpi kpi = branchKpiSourceReader.read(companyId, branch.getBranchID(), businessDate);
            kpiRepository.upsertBranchDaily(kpi);
            written++;
        }
        kpiRepository.rebuildCompanyDailyFromBranches(companyId, businessDate);

        // Cross-branch inventory snapshot for the same date (feeds low-stock / dead-stock rules).
        try {
            inventorySnapshotRepository.rebuildSnapshot(companyId, businessDate, properties.getSnapshotDeadStockDays());
        } catch (RuntimeException exception) {
            log.warn("Company inventory snapshot rebuild failed companyId={} date={} reason={}",
                    companyId, businessDate, exception.getMessage());
        }

        log.info("Company KPI aggregation companyId={} date={} branches={} durationMs={}",
                companyId, businessDate, written, (System.nanoTime() - startedAt) / 1_000_000L);
        return written;
    }

    /**
     * Daily job entry point: aggregate the target date plus the configured trailing
     * re-close window (late orders/returns correction). Each date is its own transaction.
     */
    public int aggregateWithTrailingWindow(int companyId, LocalDate targetDate) {
        int trailing = Math.max(0, properties.getTrailingRecloseDays());
        int written = 0;
        for (int offset = trailing; offset >= 0; offset--) {
            written += aggregateCompanyDate(companyId, targetDate.minusDays(offset));
        }
        return written;
    }

    /**
     * Backfill helper: aggregate an inclusive date range for one company. Used by the
     * async chunked backfill worker (one chunk = a bounded date range).
     */
    public int aggregateCompanyRange(int companyId, LocalDate fromInclusive, LocalDate toInclusive) {
        int written = 0;
        LocalDate cursor = fromInclusive;
        while (!cursor.isAfter(toInclusive)) {
            written += aggregateCompanyDate(companyId, cursor);
            cursor = cursor.plusDays(1);
        }
        return written;
    }
}
