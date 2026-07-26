package com.example.valueinsoftbackend.notification.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationRequestTest {
    @Test
    void rejectsHalfSpecifiedSubject() {
        assertThatThrownBy(() -> NotificationRequest.builder(
                        1, "pos.order.voided", "order:1")
                .subject("order", null)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subjectType");
    }

    @Test
    void rejectsInvalidTypeAndIdempotencyKeys() {
        assertThatThrownBy(() -> NotificationRequest.builder(
                1, "not valid", "key").build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationRequest.builder(
                1, "pos.order.voided", "has spaces").build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
