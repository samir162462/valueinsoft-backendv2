package com.example.valueinsoftbackend.Model.InventoryAudit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAuditAiAnalysisResponse {
    private String headline;
    private String executiveSummary;
    private String clerkQuote;
    private String riskLevel;
    private Integer score;
    private ArrayList<InventoryAuditAiInsight> highlights = new ArrayList<>();
    private ArrayList<InventoryAuditAiInsight> risks = new ArrayList<>();
    private ArrayList<InventoryAuditAiInsight> recommendations = new ArrayList<>();
    private ArrayList<InventoryAuditAiInsight> serialFindings = new ArrayList<>();
    private Instant generatedAt;
    private String model;
    private boolean aiGenerated;
    private int rowCountAnalyzed;
}
