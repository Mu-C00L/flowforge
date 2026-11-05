package io.flowforge.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flowforge.domain.ClaimedTask;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public class TaskRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;

    public TaskRepository(JdbcClient jdbc, ObjectMapper objectMapper, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.transactions = transactions;
    }

    public Optional<ClaimedTask> claimNext(String workerId, Duration leaseDuration) {
        Optional<ClaimedTask> result = transactions.execute(status -> {
            Optional<TaskCandidate> candidate = jdbc.sql("""
                            SELECT t.id, t.workflow_instance_id, t.step_id, t.handler,
                                   t.input_json::text, t.config_json::text,
                                   t.attempts, t.max_attempts
                            FROM workflow_tasks t
                            JOIN workflow_instances w ON w.id = t.workflow_instance_id
                            WHERE t.status = 'PENDING'
                              AND t.available_at <= now()
                              AND w.status = 'RUNNING'
                            ORDER BY t.available_at, t.created_at
                            FOR UPDATE OF t SKIP LOCKED
                            LIMIT 1
                            """)
                    .query((rs, ignored) -> new TaskCandidate(
                            rs.getObject("id", UUID.class),
                            rs.getObject("workflow_instance_id", UUID.class),
                            rs.getString("step_id"),
                            rs.getString("handler"),
                            rs.getString("input_json"),
                            rs.getString("config_json"),
                            rs.getInt("attempts"),
                            rs.getInt("max_attempts")))
                    .optional();

            if (candidate.isEmpty()) {
                return Optional.empty();
            }

            TaskCandidate task = candidate.get();
            OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plus(leaseDuration);
            jdbc.sql("""
                            UPDATE workflow_tasks
                            SET status = 'RUNNING', attempts = attempts + 1,
                                lease_owner = :workerId, lease_expires_at = :expiresAt,
                                updated_at = now(), error_message = NULL
                            WHERE id = :taskId
                            """)
                    .param("workerId", workerId)
                    .param("expiresAt", expiresAt)
                    .param("taskId", task.id())
                    .update();

            return Optional.of(new ClaimedTask(
                    task.id(), task.workflowId(), task.stepId(), task.handler(),
                    JsonSupport.readTree(objectMapper, task.inputJson()),
                    JsonSupport.readTree(objectMapper, task.configJson()),
                    task.attempts() + 1, task.maxAttempts()));
        });
        return result == null ? Optional.empty() : result;
    }

    public boolean markSucceeded(UUID taskId, String workerId, String outputJson) {
        return jdbc.sql("""
                        UPDATE workflow_tasks
                        SET status = 'SUCCEEDED', output_json = CAST(:output AS jsonb),
                            lease_owner = NULL, lease_expires_at = NULL, updated_at = now()
                        WHERE id = :taskId AND status = 'RUNNING' AND lease_owner = :workerId
                        """)
                .param("taskId", taskId)
                .param("workerId", workerId)
                .param("output", outputJson)
                .update() == 1;
    }

    public boolean releaseForRetry(UUID taskId, String workerId, OffsetDateTime availableAt, String error) {
        return jdbc.sql("""
                        UPDATE workflow_tasks
                        SET status = 'PENDING', available_at = :availableAt,
                            error_message = :error, lease_owner = NULL,
                            lease_expires_at = NULL, updated_at = now()
                        WHERE id = :taskId AND status = 'RUNNING' AND lease_owner = :workerId
                        """)
                .param("taskId", taskId)
                .param("workerId", workerId)
                .param("availableAt", availableAt)
                .param("error", error)
                .update() == 1;
    }

    public boolean markFailed(UUID taskId, String workerId, String error) {
        return jdbc.sql("""
                        UPDATE workflow_tasks
                        SET status = 'FAILED', error_message = :error,
                            lease_owner = NULL, lease_expires_at = NULL, updated_at = now()
                        WHERE id = :taskId AND status = 'RUNNING' AND lease_owner = :workerId
                        """)
                .param("taskId", taskId)
                .param("workerId", workerId)
                .param("error", error)
                .update() == 1;
    }

    public RecoveryResult recoverExpiredLeases() {
        RecoveryResult result = transactions.execute(status -> {
            int requeued = jdbc.sql("""
                            UPDATE workflow_tasks t
                            SET status = 'PENDING', available_at = now(), lease_owner = NULL,
                                lease_expires_at = NULL, error_message = 'Worker lease expired',
                                updated_at = now()
                            FROM workflow_instances w
                            WHERE t.workflow_instance_id = w.id
                              AND t.status = 'RUNNING'
                              AND t.lease_expires_at < now()
                              AND t.attempts < t.max_attempts
                              AND w.status = 'RUNNING'
                            """)
                    .update();

            List<ExpiredTask> exhausted = jdbc.sql("""
                            SELECT t.id, t.workflow_instance_id
                            FROM workflow_tasks t
                            JOIN workflow_instances w ON w.id = t.workflow_instance_id
                            WHERE t.status = 'RUNNING'
                              AND t.lease_expires_at < now()
                              AND t.attempts >= t.max_attempts
                              AND w.status = 'RUNNING'
                            FOR UPDATE OF t SKIP LOCKED
                            """)
                    .query((rs, ignored) -> new ExpiredTask(
                            rs.getObject("id", UUID.class),
                            rs.getObject("workflow_instance_id", UUID.class)))
                    .list();

            exhausted.forEach(task -> jdbc.sql("""
                            UPDATE workflow_tasks
                            SET status = 'FAILED', error_message = 'Worker lease expired after final attempt',
                                lease_owner = NULL, lease_expires_at = NULL, updated_at = now()
                            WHERE id = :taskId AND status = 'RUNNING'
                            """)
                    .param("taskId", task.taskId())
                    .update());

            List<UUID> failedWorkflows = exhausted.stream()
                    .map(ExpiredTask::workflowId)
                    .distinct()
                    .toList();
            failedWorkflows.forEach(workflowId -> {
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
                                WHERE workflow_instance_id = :workflowId
                                  AND status IN ('BLOCKED', 'PENDING')
                                """)
                        .param("workflowId", workflowId)
                        .update();
            });
            return new RecoveryResult(requeued, failedWorkflows);
        });
        return result == null ? new RecoveryResult(0, List.of()) : result;
    }

    public record RecoveryResult(int requeuedTasks, List<UUID> failedWorkflows) {
    }

    private record ExpiredTask(UUID taskId, UUID workflowId) {
    }

    private record TaskCandidate(
            UUID id,
            UUID workflowId,
            String stepId,
            String handler,
            String inputJson,
            String configJson,
            int attempts,
            int maxAttempts
    ) {
    }
}
