package com.example.valueinsoftbackend.Model.Public;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PublicTenantDTO {
    private String tenantCode;
    private String displayName;
    private String logoUrl;
    private String primaryColor;
    private String whatsappNumber;
    private String facebookUrl;
    private String instagramUrl;
    private String contactEmail;
    private String contactPhone;
    private String storeAddress;
    private String coverImageUrl;
    private String description;
    private String workingHours;

    public PublicTenantDTO(String tenantCode, String displayName, String logoUrl, String primaryColor) {
        this.tenantCode = tenantCode;
        this.displayName = displayName;
        this.logoUrl = logoUrl;
        this.primaryColor = primaryColor;
    }
}
