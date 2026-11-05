package io.flowforge.engine;

import java.time.Duration;

public final class BackoffPolicy {
    private static final Duration BASE_DELAY = Duration.ofSeconds(1);
    private static final Duration MAX_DELAY = Duration.ofMinutes(5);

    private BackoffPolicy() {
    }

    public static Duration forAttempt(int completedAttempt) {
        if (completedAttempt < 1) {
            throw new IllegalArgumentException("Attempt must be at least 1");
        }
        int exponent = Math.min(completedAttempt - 1, 30);
        long seconds = Math.min(MAX_DELAY.toSeconds(), BASE_DELAY.toSeconds() << exponent);
        return Duration.ofSeconds(seconds);
    }
}
