package io.flowforge.engine;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class BackoffPolicyTest {
    @Test
    void growsExponentiallyAndCapsAtFiveMinutes() {
        assertThat(BackoffPolicy.forAttempt(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(BackoffPolicy.forAttempt(4)).isEqualTo(Duration.ofSeconds(8));
        assertThat(BackoffPolicy.forAttempt(20)).isEqualTo(Duration.ofMinutes(5));
    }
}
