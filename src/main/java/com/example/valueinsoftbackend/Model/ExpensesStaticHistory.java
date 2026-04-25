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
public class ExpensesStaticHistory {
    private int id;
    private int companyId;
    @JsonProperty("staticExpenseId")
    private int staticExpenseId;
    private LocalDate dueDate;
    private LocalDate postingDate;
    private BigDecimal amount;
    private String status;
    @JsonProperty("expenseId")
    private Integer expenseId; // Reference to operational Expenses.eId
    @JsonProperty("journalEntryId")
    private UUID journalEntryId;
    private Timestamp createdAt;
    private Integer createdBy;
}
