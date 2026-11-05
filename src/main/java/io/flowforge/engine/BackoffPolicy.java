package io.flowforge.engine;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public final class BackoffPolicy {
    private static final Duration BASE_DELAY = Duration.ofSeconds(1);
    private static final Duration MAX_DELAY = Duration.ofMinutes(5);

    private BackoffPolicy() {
    }

    public static Duration forAttempt(int completedAttempt) {
        return forAttempt(completedAttempt, false);
    }

    public static Duration forAttempt(int completedAttempt, boolean withJitter) {
        if (completedAttempt < 1) {
            throw new IllegalArgumentException("Attempt must be at least 1");
        }
        int exponent = Math.min(completedAttempt - 1, 30);
        long seconds = Math.min(MAX_DELAY.toSeconds(), BASE_DELAY.toSeconds() << exponent);
        if (withJitter && seconds > 1) {
            long jitter = ThreadLocalRandom.current().nextLong(0, seconds / 2 + 1);
            seconds = Math.max(1, seconds - jitter);
        }
        return Duration.ofSeconds(seconds);
    }
}
