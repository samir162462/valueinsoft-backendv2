package com.example.valueinsoftbackend.companyinsights.engine.rule;

import com.example.valueinsoftbackend.companyinsights.ai.CompanyInsightTemplateRenderer;
import com.example.valueinsoftbackend.companyinsights.config.AllowedActionCode;
import com.example.valueinsoftbackend.companyinsights.config.CompanyInsightThresholds;
import com.example.valueinsoftbackend.companyinsights.config.InsightRole;
import com.example.valueinsoftbackend.companyinsights.config.InsightType;
import com.example.valueinsoftbackend.companyinsights.config.Severity;
import com.example.valueinsoftbackend.companyinsights.engine.BranchLabelResolver;
import com.example.valueinsoftbackend.companyinsights.engine.CompanyInsightRule;
import com.example.valueinsoftbackend.companyinsights.engine.InsightCandidate;
import com.example.valueinsoftbackend.companyinsights.kpi.CompanyInventorySnapshotRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DEAD_STOCK_COMPANY_WIDE — one atomic insight per high-value product with no movement for
 * the configured window, aggregated across all branches.
 */
@Component
public class DeadStockCompanyWideRule implements CompanyInsightRule {

    private final CompanyInventorySnapshotRepository snapshotRepository;
    private final CompanyInsightTemplateRenderer renderer;
    private final BranchLabelResolver branchLabelResolver;

    public DeadStockCompanyWideRule(CompanyInventorySnapshotRepository snapshotRepository,
                                    CompanyInsightTemplateRenderer renderer,
                                    BranchLabelResolver branchLabelResolver) {
        this.snapshotRepository = snapshotRepository;
        this.renderer = renderer;
        this.branchLabelResolver = branchLabelResolver;
    }

    @Override
    public List<InsightCandidate> evaluate(RuleContext context) {
        CompanyInsightThresholds t = context.thresholds();
        BigDecimal minValue = t.deadStockMinValue();
        List<CompanyInventorySnapshotRepository.InventorySnapshotRow> rows =
                snapshotRepository.findDeadStock(Math.toIntExact(context.companyId()), context.asOfDate(), minValue);

        int companyId = Math.toIntExact(context.companyId());
        double criticalThreshold = minValue.doubleValue() * 10.0;
        List<InsightCandidate> candidates = new ArrayList<>();
        for (CompanyInventorySnapshotRepository.InventorySnapshotRow row : rows) {
            double totalValue = row.totalValue() == null ? 0 : row.totalValue().doubleValue();
            int branches = row.branchCountWithStock();
            String lastMovement = row.lastMovementDate() == null ? null : row.lastMovementDate().toString();

            String productName = snapshotRepository.productName(companyId, row.productId());
            List<Long> affectedBranchIds = snapshotRepository.stockedBranchIds(companyId, row.productId());
            String branchNamesLabel = branchLabelResolver.labelFor(companyId, affectedBranchIds);

            Severity severity = totalValue >= criticalThreshold ? Severity.CRITICAL : Severity.WARNING;

            CompanyInsightTemplateRenderer.Rendered rendered =
                    renderer.deadStock(row.productId(), productName, totalValue, branches, lastMovement, branchNamesLabel);

            Map<String, Object> slots = new LinkedHashMap<>();
            slots.put("productId", row.productId());
            slots.put("productName", productName);
            slots.put("tiedUpValue", round2(totalValue));
            slots.put("affectedBranches", branches);
            slots.put("affectedBranchNames", branchNamesLabel);
            slots.put("lastMovementDate", lastMovement);
            slots.put("lastSaleDate", row.lastSaleDate() == null ? null : row.lastSaleDate().toString());

            Map<String, Object> sourceMetrics = new LinkedHashMap<>();
            sourceMetrics.put("totalQty", row.totalQty() == null ? 0 : row.totalQty().doubleValue());
            sourceMetrics.put("totalValue", round2(totalValue));
            sourceMetrics.put("branchCountWithStock", branches);

            Map<String, Object> actionContext = new LinkedHashMap<>();
            actionContext.put("productId", row.productId());
            actionContext.put("chip", "DEAD_STOCK");

            int priority = (severity == Severity.CRITICAL ? 300 : 200)
                    + (int) Math.min(99, Math.round(totalValue / Math.max(1.0, minValue.doubleValue())));

            candidates.add(new InsightCandidate(
                    context.companyId(),
                    InsightType.DEAD_STOCK_COMPANY_WIDE,
                    "DEADSTOCK|" + context.companyId() + "|" + row.productId(),
                    "ROLLING",
                    context.asOfDate(),
                    context.asOfDate(),
                    severity,
                    priority,
                    InsightRole.PRIMARY,
                    null,
                    rendered.title(),
                    rendered.description(),
                    rendered.summary(),
                    rendered.localized(),
                    slots,
                    BigDecimal.valueOf(round2(totalValue)),
                    affectedBranchIds,
                    List.of(row.productId()),
                    null,
                    AllowedActionCode.OPEN_DEAD_STOCK_INVENTORY,
                    actionContext,
                    sourceMetrics,
                    "COMPLETE",
                    null
            ));
        }
        return candidates;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
