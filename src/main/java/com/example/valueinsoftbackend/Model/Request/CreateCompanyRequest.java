package com.example.valueinsoftbackend.Model.Request;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class CreateCompanyRequest {

    @NotBlank(message = "companyName is required")
    private String companyName;

    @NotBlank(message = "branchName is required")
    private String branchName;

    @NotBlank(message = "plan is required")
    private String plan;

    @JsonAlias("EstablishPrice")
    @PositiveOrZero(message = "EstablishPrice must be zero or greater")
    private int establishPrice;

    @NotBlank(message = "ownerName is required")
    private String ownerName;

    @Email(message = "ownerEmail must be valid")
    private String ownerEmail;

    @Valid
    private OwnerUserRequest ownerUser;

    private String comImg;

    @NotBlank(message = "currency is required")
    private String currency;

    private String branchMajor;

    @JsonAlias("businessPackage")
    private String businessPackageId;

    public CreateCompanyRequest() {
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public int getEstablishPrice() {
        return establishPrice;
    }

    public void setEstablishPrice(int establishPrice) {
        this.establishPrice = establishPrice;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    public OwnerUserRequest getOwnerUser() {
        return ownerUser;
    }

    public void setOwnerUser(OwnerUserRequest ownerUser) {
        this.ownerUser = ownerUser;
    }

    public String getComImg() {
        return comImg;
    }

    public void setComImg(String comImg) {
        this.comImg = comImg;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getBranchMajor() {
        return branchMajor;
    }

    public void setBranchMajor(String branchMajor) {
        this.branchMajor = branchMajor;
    }

    public String getBusinessPackageId() {
        return businessPackageId;
    }

    public void setBusinessPackageId(String businessPackageId) {
        this.businessPackageId = businessPackageId;
    }

    public static class OwnerUserRequest {

        @NotBlank(message = "ownerUser.userName is required")
        @Size(max = 30, message = "ownerUser.userName must be 30 characters or fewer")
        private String userName;

        @NotBlank(message = "ownerUser.password is required")
        @Size(min = 6, max = 100, message = "ownerUser.password must be between 6 and 100 characters")
        private String password;

        @Email(message = "ownerUser.email must be valid")
        @NotBlank(message = "ownerUser.email is required")
        private String email;

        @NotBlank(message = "ownerUser.firstName is required")
        private String firstName;

        @NotBlank(message = "ownerUser.lastName is required")
        private String lastName;

        @NotBlank(message = "ownerUser.phone is required")
        private String phone;

        @PositiveOrZero(message = "ownerUser.gender must be zero or greater")
        private int gender;

        public OwnerUserRequest() {
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public int getGender() {
            return gender;
        }

        public void setGender(int gender) {
            this.gender = gender;
        }
    }
}
