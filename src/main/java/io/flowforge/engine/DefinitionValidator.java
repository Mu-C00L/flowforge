package io.flowforge.engine;

import io.flowforge.domain.StepDefinition;
import io.flowforge.domain.WorkflowDefinitionSpec;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DefinitionValidator {
    public void validate(WorkflowDefinitionSpec definition) {
        Map<String, List<String>> dependencies = new HashMap<>();
        for (StepDefinition step : definition.steps()) {
            if (dependencies.put(step.id(), step.dependsOn()) != null) {
                throw new IllegalArgumentException("Duplicate step id: " + step.id());
            }
        }
        DirectedAcyclicGraph.validate(dependencies);
    }
}
