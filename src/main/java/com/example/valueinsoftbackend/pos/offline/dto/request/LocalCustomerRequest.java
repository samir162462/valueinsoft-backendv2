package com.example.valueinsoftbackend.pos.offline.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Optional local (walk-in) customer captured at POS.
 */
public record LocalCustomerRequest(

        @Size(max = 200)
        String name,

        @Size(max = 30)
        String phone,

        @Size(max = 200)
        String email
) {}
