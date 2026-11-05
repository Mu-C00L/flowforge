package io.flowforge.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class DirectedAcyclicGraphTest {
    @Test
    void acceptsBranchesAndJoins() {
        assertThatCode(() -> DirectedAcyclicGraph.validate(Map.of(
                "reserve", List.of(),
                "charge", List.of("reserve"),
                "audit", List.of("reserve"),
                "ship", List.of("charge", "audit"))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsCycles() {
        assertThatThrownBy(() -> DirectedAcyclicGraph.validate(Map.of(
                "one", List.of("two"),
                "two", List.of("one"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("acyclic");
    }

    @Test
    void rejectsUnknownDependencies() {
        assertThatThrownBy(() -> DirectedAcyclicGraph.validate(Map.of(
                "ship", List.of("missing"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown step");
    }
}
