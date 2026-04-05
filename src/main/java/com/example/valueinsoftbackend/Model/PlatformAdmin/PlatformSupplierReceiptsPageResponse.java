package com.example.valueinsoftbackend.Model.PlatformAdmin;

import com.example.valueinsoftbackend.Model.Sales.SupplierReceipt;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSupplierReceiptsPageResponse {
    private ArrayList<SupplierReceipt> items;
    private int page;
    private int size;
    private long totalItems;
    private int totalPages;
}
