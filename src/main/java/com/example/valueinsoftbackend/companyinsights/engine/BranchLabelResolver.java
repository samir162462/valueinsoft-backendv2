package com.example.valueinsoftbackend.companyinsights.engine;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.Model.Branch;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Resolves branch IDs to a short, human-readable comma-separated label for insight text.
 */
@Component
public class BranchLabelResolver {

    private final DbBranch dbBranch;

    public BranchLabelResolver(DbBranch dbBranch) {
        this.dbBranch = dbBranch;
    }

    public Map<Long, String> namesFor(int companyId) {
        Map<Long, String> names = new HashMap<>();
        try {
            for (Branch branch : dbBranch.getBranchByCompanyId(companyId)) {
                names.put((long) branch.getBranchID(),
                        branch.getBranchName() == null ? ("Branch #" + branch.getBranchID()) : branch.getBranchName());
            }
        } catch (RuntimeException ignored) {
            // fall back to ids
        }
        return names;
    }

    public String labelFor(int companyId, List<Long> branchIds) {
        if (branchIds == null || branchIds.isEmpty()) {
            return "";
        }
        Map<Long, String> names = namesFor(companyId);
        StringJoiner joiner = new StringJoiner("، ");
        int shown = 0;
        for (Long id : branchIds) {
            if (shown >= 5) {
                joiner.add("…");
                break;
            }
            joiner.add(names.getOrDefault(id, "Branch #" + id));
            shown++;
        }
        return joiner.toString();
    }
}
