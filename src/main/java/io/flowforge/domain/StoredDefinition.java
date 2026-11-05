package io.flowforge.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StoredDefinition(
        UUID id,
        WorkflowDefinitionSpec spec,
        OffsetDateTime createdAt
) {
}
