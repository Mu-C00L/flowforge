package io.flowforge.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.flowforge.domain.ClaimedTask;
import io.flowforge.domain.StepDefinition;
import io.flowforge.domain.StoredDefinition;
import io.flowforge.domain.TaskStatus;
import io.flowforge.repository.DefinitionRepository;
import io.flowforge.repository.TaskRepository;
import io.flowforge.repository.WorkflowRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Component
public class ExecutionCoordinator {
    private final TaskRepository tasks;
    private final WorkflowRepository workflows;
    private final DefinitionRepository definitions;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;

    public ExecutionCoordinator(
            TaskRepository tasks,
            WorkflowRepository workflows,
            DefinitionRepository definitions,
            ObjectMapper objectMapper,
            TransactionTemplate transactions
    ) {
        this.tasks = tasks;
        this.workflows = workflows;
        this.definitions = definitions;
        this.objectMapper = objectMapper;
        this.transactions = transactions;
    }

    public boolean succeed(ClaimedTask task, String workerId, JsonNode output) {
        Boolean changed = transactions.execute(status -> {
            if (!tasks.markSucceeded(task.id(), workerId, write(output))) {
                return false;
            }
            ObjectNode details = objectMapper.createObjectNode().put("stepId", task.stepId());
            details.put("attempt", task.attempt());
            workflows.appendEvent(task.workflowInstanceId(), task.id(), "TASK_SUCCEEDED", details);
            advance(task.workflowInstanceId());
            return true;
        });
        return Boolean.TRUE.equals(changed);
    }

    public boolean fail(ClaimedTask task, String workerId, Throwable failure) {
        String message = safeMessage(failure);
        Boolean changed = transactions.execute(status -> {
            ObjectNode details = objectMapper.createObjectNode()
                    .put("stepId", task.stepId())
                    .put("attempt", task.attempt())
                    .put("error", message);
            if (task.attempt() < task.maxAttempts()) {
                var delay = BackoffPolicy.forAttempt(task.attempt());
                OffsetDateTime availableAt = OffsetDateTime.now(ZoneOffset.UTC).plus(delay);
                if (!tasks.releaseForRetry(task.id(), workerId, availableAt, message)) {
                    return false;
                }
                details.put("retryInSeconds", delay.toSeconds());
                workflows.appendEvent(task.workflowInstanceId(), task.id(), "TASK_RETRY_SCHEDULED", details);
            } else {
                if (!tasks.markFailed(task.id(), workerId, message)) {
                    return false;
                }
                workflows.fail(task.workflowInstanceId());
                workflows.appendEvent(task.workflowInstanceId(), task.id(), "TASK_FAILED", details);
                workflows.appendEvent(task.workflowInstanceId(), null, "WORKFLOW_FAILED", details);
            }
            return true;
        });
        return Boolean.TRUE.equals(changed);
    }

    private void advance(java.util.UUID workflowId) {
        StoredDefinition definition = definitions.findForWorkflow(workflowId);
        Map<String, TaskStatus> statuses = workflows.taskStatuses(workflowId);
        Map<String, JsonNode> outputs = workflows.taskOutputs(workflowId);

        for (StepDefinition step : definition.spec().steps()) {
            if (statuses.get(step.id()) != TaskStatus.BLOCKED) {
                continue;
            }
            boolean dependenciesSucceeded = step.dependsOn().stream()
                    .allMatch(dependency -> statuses.get(dependency) == TaskStatus.SUCCEEDED);
            if (dependenciesSucceeded) {
                ObjectNode mergedInput = objectMapper.createObjectNode();
                ObjectNode depOutputs = objectMapper.createObjectNode();
                for (String dep : step.dependsOn()) {
                    JsonNode depOut = outputs.get(dep);
                    if (depOut != null) {
                        depOutputs.set(dep, depOut);
                    }
                }
                mergedInput.set("dependencies", depOutputs);

                workflows.unblock(workflowId, step.id(), mergedInput);
                statuses.put(step.id(), TaskStatus.PENDING);
                ObjectNode details = objectMapper.createObjectNode().put("stepId", step.id());
                workflows.appendEvent(workflowId, null, "TASK_READY", details);
            }
        }

        if (statuses.values().stream().allMatch(status -> status == TaskStatus.SUCCEEDED)) {
            ObjectNode output = objectMapper.createObjectNode();
            outputs.forEach(output::set);
            workflows.complete(workflowId, output);
            workflows.appendEvent(workflowId, null, "WORKFLOW_SUCCEEDED", objectMapper.createObjectNode());
        }
    }

    private String write(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException("Step output is not serializable", exception);
        }
    }

    private static String safeMessage(Throwable failure) {
        String value = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        return value.length() <= 2_000 ? value : value.substring(0, 2_000);
    }
}
