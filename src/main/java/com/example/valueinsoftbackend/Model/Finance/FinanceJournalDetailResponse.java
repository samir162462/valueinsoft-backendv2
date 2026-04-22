package com.example.valueinsoftbackend.Model.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceJournalDetailResponse {
    private FinanceJournalEntryItem journal;
    private ArrayList<FinanceJournalLineItem> lines;
}
