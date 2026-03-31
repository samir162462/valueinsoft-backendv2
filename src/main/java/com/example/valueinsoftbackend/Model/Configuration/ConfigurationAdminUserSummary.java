package com.example.valueinsoftbackend.Model.Configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfigurationAdminUserSummary {
    private int userId;
    private String userName;
    private String email;
    private String firstName;
    private String lastName;
    private String userPhone;
    private String legacyRole;
    private int branchId;
    private String branchName;
    private Timestamp creationTime;
}
