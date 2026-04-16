package com.example.valueinsoftbackend.Model.Request;

import jakarta.validation.constraints.NotBlank;

public class UpdateCompanyImageRequest {

    @NotBlank(message = "imgFile is required")
    private String imgFile;

    public UpdateCompanyImageRequest() {
    }

    public String getImgFile() {
        return imgFile;
    }

    public void setImgFile(String imgFile) {
        this.imgFile = imgFile;
    }
}
