package io.flowforge.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public record StepDefinition(
        @NotBlank String id,
        @NotBlank String handler,
        List<String> dependsOn,
        @Min(1) @Max(20) int maxAttempts,
        Map<String, Object> config
) {
    public StepDefinition {
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        maxAttempts = maxAttempts == 0 ? 3 : maxAttempts;
        config = config == null ? Map.of() : Map.copyOf(config);
    }
}
