package com.example.valueinsoftbackend.Model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class ExpensesStatic {
    @JsonProperty("eId")
    private int eId;
    private String type;
    private BigDecimal amount;
    private Timestamp time;
    @JsonProperty("branchId")
    private int branchId;
    private String user;
    private String name;
    private String period;
    private UUID expenseAccountId;
    private UUID paymentAccountId;
    private UUID postedJournalEntryId; // Legacy field, might keep for compatibility
    
    // New Recurrence fields
    private LocalDate lastPostedDate;
    private LocalDate nextDueDate;
    private boolean autoPostEnabled;
}
