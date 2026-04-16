package com.example.valueinsoftbackend.Model.Request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;

public class CashMovementRequest {

    @NotBlank(message = "movementType is required")
    private String movementType;

    @Positive(message = "amount must be positive")
    private double amount;

    private String note;
    private Integer clientId;
    private String associatedUserId;
    private String referenceType;
    private String referenceId;

    public CashMovementRequest() {}

    public String getMovementType() { return movementType; }
    public void setMovementType(String movementType) { this.movementType = movementType; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Integer getClientId() { return clientId; }
    public void setClientId(Integer clientId) { this.clientId = clientId; }

    public String getAssociatedUserId() { return associatedUserId; }
    public void setAssociatedUserId(String associatedUserId) { this.associatedUserId = associatedUserId; }

    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }

    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
}
