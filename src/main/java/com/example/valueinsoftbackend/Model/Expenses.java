/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
@EqualsAndHashCode
public class Expenses {

    int exId ;
    String type;
    BigDecimal amount;
    Timestamp time ;
    int branchId;
    String user;
    String name;

}
