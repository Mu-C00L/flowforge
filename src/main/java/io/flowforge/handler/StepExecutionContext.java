package io.flowforge.handler;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;
import java.util.function.Consumer;

public record StepExecutionContext(
        UUID workflowId,
        UUID taskId,
        String stepId,
        int attempt,
        JsonNode workflowInput,
        JsonNode config,
        Consumer<UUID> heartbeatConsumer
) {
    public StepExecutionContext(
            UUID workflowId,
            UUID taskId,
            String stepId,
            int attempt,
            JsonNode workflowInput,
            JsonNode config
    ) {
        this(workflowId, taskId, stepId, attempt, workflowInput, config, id -> {});
    }

    public void heartbeat() {
        if (heartbeatConsumer != null) {
            heartbeatConsumer.accept(taskId);
        }
    }
}
