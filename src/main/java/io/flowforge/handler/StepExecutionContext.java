package io.flowforge.handler;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record StepExecutionContext(
        UUID workflowId,
        UUID taskId,
        String stepId,
        int attempt,
        JsonNode workflowInput,
        JsonNode config
) {
}
