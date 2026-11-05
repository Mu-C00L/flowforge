package io.flowforge.handler;

import com.fasterxml.jackson.databind.JsonNode;

public interface StepHandler {
    String name();

    JsonNode execute(StepExecutionContext context) throws Exception;
}
