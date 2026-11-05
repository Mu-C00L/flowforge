package io.flowforge.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class FailNTimesStepHandler implements StepHandler {
    private final ObjectMapper objectMapper;

    public FailNTimesStepHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "fail-n-times";
    }

    @Override
    public JsonNode execute(StepExecutionContext context) {
        int failures = Math.max(0, context.config().path("failures").asInt(1));
        if (context.attempt() <= failures) {
            throw new IllegalStateException("Planned failure " + context.attempt() + " of " + failures);
        }
        return objectMapper.createObjectNode()
                .put("succeededOnAttempt", context.attempt());
    }
}
