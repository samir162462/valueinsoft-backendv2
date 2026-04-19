package com.example.valueinsoftbackend.Model.InventoryWorkspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDateRange {
    private String preset;
    private String from;
    private String to;
}
