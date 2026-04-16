package com.example.valueinsoftbackend.Model.Request.BranchSettings;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BranchSettingsBatchUpdateRequest {
    @NotNull
    @Valid
    private ArrayList<BranchSettingValueInput> items = new ArrayList<>();
}
