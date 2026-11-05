package io.flowforge.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.flowforge.domain.StepDefinition;
import io.flowforge.domain.TaskSnapshot;
import io.flowforge.domain.TaskStatus;
import io.flowforge.domain.WorkflowEvent;
import io.flowforge.domain.WorkflowSnapshot;
import io.flowforge.domain.WorkflowStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class WorkflowRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public WorkflowRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void insertWorkflow(UUID id, UUID definitionId, JsonNode input, String idempotencyKey) {
        jdbc.sql("""
                        INSERT INTO workflow_instances
                            (id, definition_id, status, input_json, idempotency_key)
                        VALUES
                            (:id, :definitionId, 'RUNNING', CAST(:input AS jsonb), :idempotencyKey)
                        """)
                .param("id", id)
                .param("definitionId", definitionId)
                .param("input", JsonSupport.write(objectMapper, input))
                .param("idempotencyKey", idempotencyKey)
                .update();
    }

    public void insertTask(UUID workflowId, StepDefinition step, JsonNode input) {
        TaskStatus status = step.dependsOn().isEmpty() ? TaskStatus.PENDING : TaskStatus.BLOCKED;
        jdbc.sql("""
                        INSERT INTO workflow_tasks
                            (id, workflow_instance_id, step_id, handler, status, input_json,
                             config_json, max_attempts, available_at)
                        VALUES
                            (:id, :workflowId, :stepId, :handler, :status, CAST(:input AS jsonb),
                             CAST(:config AS jsonb), :maxAttempts,
                             CASE WHEN :status = 'PENDING' THEN now() ELSE NULL END)
                        """)
                .param("id", UUID.randomUUID())
                .param("workflowId", workflowId)
                .param("stepId", step.id())
                .param("handler", step.handler())
                .param("status", status.name())
                .param("input", JsonSupport.write(objectMapper, input))
                .param("config", JsonSupport.write(objectMapper, step.config()))
                .param("maxAttempts", step.maxAttempts())
                .update();
    }

    public Optional<UUID> findByIdempotencyKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT id FROM workflow_instances WHERE idempotency_key = :key")
                .param("key", key)
                .query(UUID.class)
                .optional();
    }

    public Optional<WorkflowSnapshot> findSnapshot(UUID workflowId) {
        Optional<WorkflowHeader> header = jdbc.sql("""
                        SELECT w.id, d.name, d.version, w.status, w.input_json::text,
                               w.output_json::text, w.created_at, w.completed_at
                        FROM workflow_instances w
                        JOIN workflow_definitions d ON d.id = w.definition_id
                        WHERE w.id = :workflowId
                        """)
                .param("workflowId", workflowId)
                .query((rs, ignored) -> new WorkflowHeader(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getInt("version"),
                        WorkflowStatus.valueOf(rs.getString("status")),
                        JsonSupport.readTree(objectMapper, rs.getString("input_json")),
                        JsonSupport.readTree(objectMapper, rs.getString("output_json")),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("completed_at", OffsetDateTime.class)))
                .optional();

        return header.map(value -> new WorkflowSnapshot(
                value.id(), value.name(), value.version(), value.status(), value.input(), value.output(),
                value.createdAt(), value.completedAt(), listTasks(workflowId)));
    }

    public List<TaskSnapshot> listTasks(UUID workflowId) {
        return jdbc.sql("""
                        SELECT id, step_id, handler, status, attempts, max_attempts,
                               output_json::text, error_message, available_at, updated_at
                        FROM workflow_tasks
                        WHERE workflow_instance_id = :workflowId
                        ORDER BY created_at, step_id
                        """)
                .param("workflowId", workflowId)
                .query((rs, ignored) -> new TaskSnapshot(
                        rs.getObject("id", UUID.class),
                        rs.getString("step_id"),
                        rs.getString("handler"),
                        TaskStatus.valueOf(rs.getString("status")),
                        rs.getInt("attempts"),
                        rs.getInt("max_attempts"),
                        JsonSupport.readTree(objectMapper, rs.getString("output_json")),
                        rs.getString("error_message"),
                        rs.getObject("available_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class)))
                .list();
    }

    public Map<String, TaskStatus> taskStatuses(UUID workflowId) {
        Map<String, TaskStatus> statuses = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT step_id, status FROM workflow_tasks
                        WHERE workflow_instance_id = :workflowId
                        """)
                .param("workflowId", workflowId)
                .query((rs, ignored) -> Map.entry(
                        rs.getString("step_id"), TaskStatus.valueOf(rs.getString("status"))))
                .list()
                .forEach(entry -> statuses.put(entry.getKey(), entry.getValue()));
        return statuses;
    }

    public Map<String, JsonNode> taskOutputs(UUID workflowId) {
        Map<String, JsonNode> outputs = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT step_id, output_json::text FROM workflow_tasks
                        WHERE workflow_instance_id = :workflowId AND status = 'SUCCEEDED'
                        ORDER BY created_at, step_id
                        """)
                .param("workflowId", workflowId)
                .query((rs, ignored) -> Map.entry(
                        rs.getString("step_id"), JsonSupport.readTree(objectMapper, rs.getString("output_json"))))
                .list()
                .forEach(entry -> outputs.put(entry.getKey(), entry.getValue()));
        return outputs;
    }

    public void unblock(UUID workflowId, String stepId) {
        jdbc.sql("""
                        UPDATE workflow_tasks
                        SET status = 'PENDING', available_at = now(), updated_at = now()
                        WHERE workflow_instance_id = :workflowId
                          AND step_id = :stepId
                          AND status = 'BLOCKED'
                        """)
                .param("workflowId", workflowId)
                .param("stepId", stepId)
                .update();
    }

    public void complete(UUID workflowId, JsonNode output) {
        jdbc.sql("""
                        UPDATE workflow_instances
                        SET status = 'SUCCEEDED', output_json = CAST(:output AS jsonb), completed_at = now()
                        WHERE id = :workflowId AND status = 'RUNNING'
                        """)
                .param("workflowId", workflowId)
                .param("output", JsonSupport.write(objectMapper, output))
                .update();
    }

    public void fail(UUID workflowId) {
        jdbc.sql("""
                        UPDATE workflow_instances
                        SET status = 'FAILED', completed_at = now()
                        WHERE id = :workflowId AND status = 'RUNNING'
                        """)
                .param("workflowId", workflowId)
                .update();
        jdbc.sql("""
                        UPDATE workflow_tasks
                        SET status = 'CANCELED', updated_at = now()
                        WHERE workflow_instance_id = :workflowId AND status IN ('BLOCKED', 'PENDING')
                        """)
                .param("workflowId", workflowId)
                .update();
    }

    public boolean cancel(UUID workflowId) {
        int changed = jdbc.sql("""
                        UPDATE workflow_instances
                        SET status = 'CANCELED', completed_at = now()
                        WHERE id = :workflowId AND status = 'RUNNING'
                        """)
                .param("workflowId", workflowId)
                .update();
        if (changed > 0) {
            jdbc.sql("""
                            UPDATE workflow_tasks
                            SET status = 'CANCELED', updated_at = now()
                            WHERE workflow_instance_id = :workflowId
                              AND status IN ('BLOCKED', 'PENDING', 'RUNNING')
                            """)
                    .param("workflowId", workflowId)
                    .update();
        }
        return changed > 0;
    }

    public void appendEvent(UUID workflowId, UUID taskId, String type, JsonNode details) {
        jdbc.sql("""
                        INSERT INTO workflow_events
                            (workflow_instance_id, task_id, event_type, details_json)
                        VALUES
                            (:workflowId, :taskId, :type, CAST(:details AS jsonb))
                        """)
                .param("workflowId", workflowId)
                .param("taskId", taskId)
                .param("type", type)
                .param("details", JsonSupport.write(objectMapper, details))
                .update();
    }

    public List<WorkflowEvent> listEvents(UUID workflowId) {
        return jdbc.sql("""
                        SELECT sequence_id, task_id, event_type, details_json::text, occurred_at
                        FROM workflow_events
                        WHERE workflow_instance_id = :workflowId
                        ORDER BY sequence_id
                        """)
                .param("workflowId", workflowId)
                .query((rs, ignored) -> new WorkflowEvent(
                        rs.getLong("sequence_id"),
                        rs.getObject("task_id", UUID.class),
                        rs.getString("event_type"),
                        JsonSupport.readTree(objectMapper, rs.getString("details_json")),
                        rs.getObject("occurred_at", OffsetDateTime.class)))
                .list();
    }

    private record WorkflowHeader(
            UUID id,
            String name,
            int version,
            WorkflowStatus status,
            JsonNode input,
            JsonNode output,
            OffsetDateTime createdAt,
            OffsetDateTime completedAt
    ) {
    }
}
