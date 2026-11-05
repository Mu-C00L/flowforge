package io.flowforge.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class DelayStepHandler implements StepHandler {
    private static final long MAX_DELAY_MS = 10_000;
    private final ObjectMapper objectMapper;

    public DelayStepHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "delay";
    }

    @Override
    public JsonNode execute(StepExecutionContext context) throws InterruptedException {
        long requested = context.config().path("milliseconds").asLong(100);
        long delay = Math.max(0, Math.min(requested, MAX_DELAY_MS));
        Thread.sleep(delay);
        return objectMapper.createObjectNode().put("delayedMilliseconds", delay);
    }
}
