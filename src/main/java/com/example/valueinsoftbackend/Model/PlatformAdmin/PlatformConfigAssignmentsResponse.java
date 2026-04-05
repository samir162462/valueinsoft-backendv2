package com.example.valueinsoftbackend.Model.PlatformAdmin;

import com.example.valueinsoftbackend.Model.Configuration.RoleDefinitionConfig;
import com.example.valueinsoftbackend.Model.Configuration.RoleGrantConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantRoleAssignmentConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformConfigAssignmentsResponse {
    private ArrayList<RoleDefinitionConfig> roleDefinitions;
    private ArrayList<RoleGrantConfig> roleGrants;
    private ArrayList<TenantRoleAssignmentConfig> roleAssignments;
}
