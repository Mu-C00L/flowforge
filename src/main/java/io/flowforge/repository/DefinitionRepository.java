package io.flowforge.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flowforge.domain.StoredDefinition;
import io.flowforge.domain.WorkflowDefinitionSpec;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DefinitionRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public DefinitionRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public StoredDefinition create(WorkflowDefinitionSpec spec) {
        UUID id = UUID.randomUUID();
        String json = JsonSupport.write(objectMapper, spec);
        return jdbc.sql("""
                        INSERT INTO workflow_definitions (id, name, version, definition_json)
                        VALUES (:id, :name, :version, CAST(:definition AS jsonb))
                        RETURNING id, definition_json::text, created_at
                        """)
                .param("id", id)
                .param("name", spec.name())
                .param("version", spec.version())
                .param("definition", json)
                .query((rs, ignored) -> map(rs))
                .single();
    }

    public Optional<StoredDefinition> find(String name, int version) {
        return jdbc.sql("""
                        SELECT id, definition_json::text, created_at
                        FROM workflow_definitions
                        WHERE name = :name AND version = :version
                        """)
                .param("name", name)
                .param("version", version)
                .query((rs, ignored) -> map(rs))
                .optional();
    }

    public StoredDefinition findForWorkflow(UUID workflowId) {
        return jdbc.sql("""
                        SELECT d.id, d.definition_json::text, d.created_at
                        FROM workflow_definitions d
                        JOIN workflow_instances w ON w.definition_id = d.id
                        WHERE w.id = :workflowId
                        """)
                .param("workflowId", workflowId)
                .query((rs, ignored) -> map(rs))
                .single();
    }

    private StoredDefinition map(ResultSet rs) throws SQLException {
        return new StoredDefinition(
                rs.getObject("id", UUID.class),
                JsonSupport.read(objectMapper, rs.getString("definition_json"), WorkflowDefinitionSpec.class),
                rs.getObject("created_at", java.time.OffsetDateTime.class));
    }
}
