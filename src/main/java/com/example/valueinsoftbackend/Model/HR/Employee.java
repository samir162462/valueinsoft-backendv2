package com.example.valueinsoftbackend.Model.HR;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Employee {
    private int id;
    private int companyId;
    private int branchId;
    private String employeeCode;
    private String pinHash;
    private String firstName;
    private String lastName;
    private Integer userId;
    private boolean isActive;
    private Timestamp createdAt;
    private String createdBy;
    private Timestamp updatedAt;
    private String updatedBy;
}
