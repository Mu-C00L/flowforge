package io.flowforge.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record ClaimedTask(
        UUID id,
        UUID workflowInstanceId,
        String stepId,
        String handler,
        JsonNode input,
        JsonNode config,
        int attempt,
        int maxAttempts
) {
}
