package io.flowforge.engine;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class CoreSmokeCheck {
    public static void main(String[] args) {
        DirectedAcyclicGraph.validate(Map.of(
                "reserve", List.of(),
                "charge", List.of("reserve"),
                "audit", List.of("reserve"),
                "ship", List.of("charge", "audit")));

        boolean rejectedCycle = false;
        try {
            DirectedAcyclicGraph.validate(Map.of(
                    "one", List.of("two"),
                    "two", List.of("one")));
        } catch (IllegalArgumentException expected) {
            rejectedCycle = true;
        }
        if (!rejectedCycle) {
            throw new AssertionError("Cycle was not rejected");
        }
        if (!BackoffPolicy.forAttempt(4).equals(Duration.ofSeconds(8))) {
            throw new AssertionError("Backoff policy returned the wrong delay");
        }
        if (!BackoffPolicy.forAttempt(20).equals(Duration.ofMinutes(5))) {
            throw new AssertionError("Backoff policy did not cap the delay");
        }
        System.out.println("Core scheduling checks passed");
    }
}
