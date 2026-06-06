package com.example.valueinsoftbackend.customerbehavior.security;

import com.example.valueinsoftbackend.ai.service.AiSecurityContext;

import java.util.List;

public record CustomerBehaviorRequestContext(
        AiSecurityContext aiContext,
        List<Integer> branchIds
) {
    public long companyId() {
        return aiContext.companyId();
    }

    public long userId() {
        return aiContext.userId();
    }

    public String username() {
        return aiContext.username();
    }
}
