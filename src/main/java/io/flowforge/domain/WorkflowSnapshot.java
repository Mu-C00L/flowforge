package io.flowforge.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record WorkflowSnapshot(
        UUID id,
        String definitionName,
        int definitionVersion,
        WorkflowStatus status,
        JsonNode input,
        JsonNode output,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        List<TaskSnapshot> tasks
) {
}
