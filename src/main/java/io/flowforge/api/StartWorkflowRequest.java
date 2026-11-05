package io.flowforge.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record StartWorkflowRequest(
        @NotBlank String definitionName,
        @Min(1) int definitionVersion,
        JsonNode input
) {
}
