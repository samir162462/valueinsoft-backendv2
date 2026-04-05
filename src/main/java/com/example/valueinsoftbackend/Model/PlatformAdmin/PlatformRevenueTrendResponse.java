package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformRevenueTrendResponse {
    private Integer tenantFilter;
    private String packageFilter;
    private int days;
    private ArrayList<PlatformRevenueTrendPoint> points;
    private Timestamp generatedAt;
}
