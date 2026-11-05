package io.flowforge.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class IdempotentOperationHandler implements StepHandler {
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public IdempotentOperationHandler(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "idempotent-operation";
    }

    @Override
    public JsonNode execute(StepExecutionContext context) {
        String operation = context.config().path("operation").asText(context.stepId());
        ObjectNode proposedResult = objectMapper.createObjectNode()
                .put("operation", operation)
                .put("reference", context.workflowId() + ":" + context.stepId())
                .put("committedByAttempt", context.attempt());

        jdbc.sql("""
                        INSERT INTO handler_execution_ledger (task_id, operation, result_json)
                        VALUES (:taskId, :operation, CAST(:result AS jsonb))
                        ON CONFLICT (task_id) DO NOTHING
                        """)
                .param("taskId", context.taskId())
                .param("operation", operation)
                .param("result", write(proposedResult))
                .update();

        if (context.config().path("simulatePostCommitFailureOnAttempt").asInt(-1) == context.attempt()) {
            throw new IllegalStateException("Simulated crash after committing side effect");
        }

        String stored = jdbc.sql("""
                        SELECT result_json::text FROM handler_execution_ledger WHERE task_id = :taskId
                        """)
                .param("taskId", context.taskId())
                .query(String.class)
                .single();
        try {
            return objectMapper.readTree(stored);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored handler result is invalid", exception);
        }
    }

    private String write(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Handler result is not serializable", exception);
        }
    }
}
