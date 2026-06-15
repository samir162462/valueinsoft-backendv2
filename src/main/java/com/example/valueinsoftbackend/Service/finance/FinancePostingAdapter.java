package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;

import java.util.UUID;

public interface FinancePostingAdapter {

    boolean supports(String sourceModule);

    UUID post(FinancePostingRequestItem request);
}
