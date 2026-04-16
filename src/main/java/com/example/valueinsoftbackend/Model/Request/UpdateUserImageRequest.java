package com.example.valueinsoftbackend.Model.Request;

import jakarta.validation.constraints.NotBlank;

public class UpdateUserImageRequest {

    @NotBlank(message = "imgFile is required")
    private String imgFile;

    public UpdateUserImageRequest() {
    }

    public String getImgFile() {
        return imgFile;
    }

    public void setImgFile(String imgFile) {
        this.imgFile = imgFile;
    }
}
