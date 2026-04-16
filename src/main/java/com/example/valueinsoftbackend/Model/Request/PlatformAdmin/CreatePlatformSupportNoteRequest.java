package com.example.valueinsoftbackend.Model.Request.PlatformAdmin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class CreatePlatformSupportNoteRequest {

    @NotNull
    @Positive
    private Integer tenantId;

    @Positive
    private Integer branchId;

    @NotBlank
    @Size(max = 20)
    private String noteType;

    @NotBlank
    @Size(max = 160)
    private String subject;

    @NotBlank
    @Size(max = 4000)
    private String body;

    @NotBlank
    @Size(max = 20)
    private String visibility;

    public Integer getTenantId() {
        return tenantId;
    }

    public void setTenantId(Integer tenantId) {
        this.tenantId = tenantId;
    }

    public Integer getBranchId() {
        return branchId;
    }

    public void setBranchId(Integer branchId) {
        this.branchId = branchId;
    }

    public String getNoteType() {
        return noteType;
    }

    public void setNoteType(String noteType) {
        this.noteType = noteType;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }
}
