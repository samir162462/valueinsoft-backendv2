package com.example.valueinsoftbackend.Model.PlatformAdmin;

import com.example.valueinsoftbackend.Model.Expenses;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformExpensesPageResponse {
    private ArrayList<Expenses> items;
    private int page;
    private int size;
    private long totalItems;
    private int totalPages;
}
