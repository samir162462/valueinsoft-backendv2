package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;
import org.springframework.http.HttpStatus;

import java.util.UUID;

abstract class FinanceUnsupportedPostingAdapterSupport implements FinancePostingAdapter {

    @Override
    public UUID post(FinancePostingRequestItem request) {
        throw new ApiException(
                HttpStatus.NOT_IMPLEMENTED,
                "FINANCE_POSTING_ADAPTER_NOT_IMPLEMENTED",
                "Finance posting adapter for " + request.getSourceModule() + " is not implemented yet");
    }
}
