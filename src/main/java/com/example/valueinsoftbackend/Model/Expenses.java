/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Expenses {

    @JsonProperty("eId")
    int eId;
    String type;
    BigDecimal amount;
    Timestamp time;
    int branchId;
    String user;
    String name;
    String period;
    java.util.UUID expenseAccountId;
    java.util.UUID paymentAccountId;
    java.util.UUID postedJournalEntryId;
    java.time.LocalDate nextDueDate;

}
