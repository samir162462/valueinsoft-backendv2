/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model.Sales;

import lombok.*;

import java.sql.Date;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
@EqualsAndHashCode
public class ExpensesSum {
    Date date ;
    int amount;
    int count;
}
