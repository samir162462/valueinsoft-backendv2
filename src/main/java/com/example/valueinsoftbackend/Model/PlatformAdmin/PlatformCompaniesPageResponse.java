package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformCompaniesPageResponse {
    private ArrayList<PlatformCompanyListItem> items;
    private int page;
    private int size;
    private long totalItems;
    private int totalPages;
}
