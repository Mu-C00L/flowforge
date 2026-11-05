package io.flowforge.handler;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class StepHandlerRegistry {
    private final Map<String, StepHandler> handlers;

    public StepHandlerRegistry(List<StepHandler> handlers) {
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(
                StepHandler::name, Function.identity()));
    }

    public StepHandler require(String name) {
        StepHandler handler = handlers.get(name);
        if (handler == null) {
            throw new IllegalArgumentException("Unknown step handler: " + name);
        }
        return handler;
    }

    public boolean contains(String name) {
        return handlers.containsKey(name);
    }
}
