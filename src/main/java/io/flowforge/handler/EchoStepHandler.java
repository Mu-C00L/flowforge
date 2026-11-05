package io.flowforge.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class EchoStepHandler implements StepHandler {
    private final ObjectMapper objectMapper;

    public EchoStepHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "echo";
    }

    @Override
    public JsonNode execute(StepExecutionContext context) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("step", context.stepId());
        result.put("attempt", context.attempt());
        result.set("input", context.workflowInput());
        result.set("config", context.config());
        return result;
    }
}
