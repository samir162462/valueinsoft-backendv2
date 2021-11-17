package com.example.valueinsoftbackend.Model;

public class Client {
    int clientId;
    String clientName;
    String clientPhone;
    String gender;
    String description;


    public Client(int clientId, String clientName, String clientPhone, String gender, String description) {
        this.clientId = clientId;
        this.clientName = clientName;
        this.clientPhone = clientPhone;
        this.gender = gender;
        this.description = description;
    }

    public int getClientId() {
        return clientId;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getClientPhone() {
        return clientPhone;
    }

    public void setClientPhone(String clientPhone) {
        this.clientPhone = clientPhone;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
