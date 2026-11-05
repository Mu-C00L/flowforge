package io.flowforge.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record WorkflowDefinitionSpec(
        @NotBlank String name,
        @Min(1) int version,
        @NotEmpty List<@Valid StepDefinition> steps
) {
    public WorkflowDefinitionSpec {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
