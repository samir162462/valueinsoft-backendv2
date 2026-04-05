package com.example.valueinsoftbackend.Model.Request.PlatformAdmin;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class PlatformLifecycleActionRequest {

    @NotBlank
    @Size(max = 120)
    private String reason;

    @Size(max = 500)
    private String note;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
