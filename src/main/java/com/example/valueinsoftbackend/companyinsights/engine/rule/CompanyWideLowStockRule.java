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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * COMPANY_WIDE_LOW_STOCK — one atomic insight per product that is below reorder in
 * enough branches (or out of stock in any branch). Surfaces transfer opportunities but
 * never automates transfers.
 */
@Component
public class CompanyWideLowStockRule implements CompanyInsightRule {

    private final CompanyInventorySnapshotRepository snapshotRepository;
    private final CompanyInsightTemplateRenderer renderer;
    private final BranchLabelResolver branchLabelResolver;

    public CompanyWideLowStockRule(CompanyInventorySnapshotRepository snapshotRepository,
                                   CompanyInsightTemplateRenderer renderer,
                                   BranchLabelResolver branchLabelResolver) {
        this.snapshotRepository = snapshotRepository;
        this.renderer = renderer;
        this.branchLabelResolver = branchLabelResolver;
    }

    @Override
    public List<InsightCandidate> evaluate(RuleContext context) {
        CompanyInsightThresholds t = context.thresholds();
        List<CompanyInventorySnapshotRepository.InventorySnapshotRow> rows =
                snapshotRepository.findLowStock(Math.toIntExact(context.companyId()), context.asOfDate(),
                        t.lowStockMultiBranchCount());

        int companyId = Math.toIntExact(context.companyId());
        List<InsightCandidate> candidates = new ArrayList<>();
        for (CompanyInventorySnapshotRepository.InventorySnapshotRow row : rows) {
            int affectedBranches = Math.max(row.branchesBelowReorder(), row.branchesOutOfStock());
            boolean transferPossible = row.branchCountWithStock() > 0 && row.branchesOutOfStock() > 0;
            double totalQty = row.totalQty() == null ? 0 : row.totalQty().doubleValue();

            String productName = snapshotRepository.productName(companyId, row.productId());
            List<Long> affectedBranchIds = snapshotRepository.lowStockBranchIds(companyId, row.productId());
            String branchNamesLabel = branchLabelResolver.labelFor(companyId, affectedBranchIds);

            Severity severity = row.branchesOutOfStock() > 0 ? Severity.CRITICAL : Severity.WARNING;

            CompanyInsightTemplateRenderer.Rendered rendered =
                    renderer.companyWideLowStock(row.productId(), productName, affectedBranches, totalQty,
                            transferPossible, branchNamesLabel);

            Map<String, Object> slots = new LinkedHashMap<>();
            slots.put("productId", row.productId());
            slots.put("productName", productName);
            slots.put("affectedBranches", affectedBranches);
            slots.put("affectedBranchNames", branchNamesLabel);
            slots.put("totalCompanyQuantity", totalQty);
            slots.put("branchesOutOfStock", row.branchesOutOfStock());
            slots.put("transferPossible", transferPossible);

            Map<String, Object> sourceMetrics = new LinkedHashMap<>();
            sourceMetrics.put("branchesBelowReorder", row.branchesBelowReorder());
            sourceMetrics.put("branchesOutOfStock", row.branchesOutOfStock());
            sourceMetrics.put("branchCountWithStock", row.branchCountWithStock());
            sourceMetrics.put("totalQty", totalQty);

            Map<String, Object> actionContext = new LinkedHashMap<>();
            actionContext.put("productId", row.productId());
            actionContext.put("chip", "LOW_STOCK");

            int priority = (severity == Severity.CRITICAL ? 300 : 200) + Math.min(99, affectedBranches * 5);

            candidates.add(new InsightCandidate(
                    context.companyId(),
                    InsightType.COMPANY_WIDE_LOW_STOCK,
                    "LOWSTOCK|" + context.companyId() + "|" + row.productId(),
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
                    null,
                    affectedBranchIds,
                    List.of(row.productId()),
                    null,
                    AllowedActionCode.OPEN_LOW_STOCK_INVENTORY,
                    actionContext,
                    sourceMetrics,
                    "COMPLETE",
                    null
            ));
        }
        return candidates;
    }
}
