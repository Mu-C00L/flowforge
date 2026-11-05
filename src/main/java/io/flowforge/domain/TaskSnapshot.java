package io.flowforge.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TaskSnapshot(
        UUID id,
        String stepId,
        String handler,
        TaskStatus status,
        int attempts,
        int maxAttempts,
        JsonNode output,
        String errorMessage,
        OffsetDateTime availableAt,
        OffsetDateTime updatedAt
) {
}
