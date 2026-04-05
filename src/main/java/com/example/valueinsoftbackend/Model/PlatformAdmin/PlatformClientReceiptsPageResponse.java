package com.example.valueinsoftbackend.Model.PlatformAdmin;

import com.example.valueinsoftbackend.Model.Sales.ClientReceipt;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformClientReceiptsPageResponse {
    private ArrayList<ClientReceipt> items;
    private int page;
    private int size;
    private long totalItems;
    private int totalPages;
}
