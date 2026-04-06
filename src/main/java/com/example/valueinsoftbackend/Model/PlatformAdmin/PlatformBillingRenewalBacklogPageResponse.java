package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformBillingRenewalBacklogPageResponse {
    private ArrayList<PlatformBillingRenewalBacklogItem> items;
    private int page;
    private int size;
    private long totalItems;
    private int totalPages;
}
