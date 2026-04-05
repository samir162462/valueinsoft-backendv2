package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSupportNoteItem {
    private long noteId;
    private int tenantId;
    private String companyName;
    private Integer branchId;
    private String branchName;
    private String noteType;
    private String subject;
    private String body;
    private String visibility;
    private Integer createdByUserId;
    private String createdByUserName;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
