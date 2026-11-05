package io.flowforge.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkflowEvent(
        long sequenceId,
        UUID taskId,
        String eventType,
        JsonNode details,
        OffsetDateTime occurredAt
) {
}
