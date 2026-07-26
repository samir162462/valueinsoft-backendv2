package com.example.valueinsoftbackend.notification.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationCursorCodecTest {
    private final NotificationCursorCodec codec = new NotificationCursorCodec();

    @Test
    void cursorRoundTripsWithoutExposingTupleSyntax() {
        Instant time = Instant.parse("2026-07-25T12:34:56.123Z");
        String encoded = codec.encode(time, 55);

        assertThat(encoded).doesNotContain(time.toString(), "|");
        assertThat(codec.decode(encoded))
                .isEqualTo(new NotificationCursorCodec.Cursor(time, 55));
    }

    @Test
    void malformedCursorIsRejected() {
        assertThatThrownBy(() -> codec.decode("not-a-cursor"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
