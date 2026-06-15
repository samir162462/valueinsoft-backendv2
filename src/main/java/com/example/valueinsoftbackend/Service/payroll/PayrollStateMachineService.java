package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class PayrollStateMachineService {

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "DRAFT", Set.of("CALCULATED", "CANCELLED"),
            "CALCULATED", Set.of("APPROVED", "CANCELLED"),
            "APPROVED", Set.of("POSTING_IN_PROGRESS"),
            "POSTING_IN_PROGRESS", Set.of("POSTED", "FAILED_POSTING"),
            "FAILED_POSTING", Set.of("POSTING_IN_PROGRESS"),
            "POSTED", Set.of("PARTIALLY_PAID", "PAID", "REVERSED"),
            "PARTIALLY_PAID", Set.of("PAID"),
            "PAID", Set.of(),
            "CANCELLED", Set.of(),
            "REVERSED", Set.of()
    );

    private static final Set<String> EDITABLE_RUN_STATUSES = Set.of("DRAFT", "CALCULATED");

    public void assertTransition(String current, String target) {
        if (current == null || target == null || !TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYROLL_INVALID_STATUS_TRANSITION",
                    "Payroll status cannot transition from " + current + " to " + target);
        }
    }

    public void validateRunEditable(String status) {
        if (!EDITABLE_RUN_STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYROLL_RUN_NOT_EDITABLE",
                    "Only DRAFT or CALCULATED payroll runs can be edited");
        }
    }
}
