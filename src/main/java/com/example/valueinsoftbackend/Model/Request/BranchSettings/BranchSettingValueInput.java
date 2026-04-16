package com.example.valueinsoftbackend.Model.Request.BranchSettings;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BranchSettingValueInput {
    @NotBlank
    private String settingKey;
    private Object value;
}
