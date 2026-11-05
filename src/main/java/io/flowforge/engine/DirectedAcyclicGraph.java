package io.flowforge.engine;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DirectedAcyclicGraph {
    private DirectedAcyclicGraph() {
    }

    public static void validate(Map<String, List<String>> dependencies) {
        if (dependencies.isEmpty()) {
            throw new IllegalArgumentException("A workflow must contain at least one step");
        }

        for (Map.Entry<String, List<String>> entry : dependencies.entrySet()) {
            for (String dependency : entry.getValue()) {
                if (!dependencies.containsKey(dependency)) {
                    throw new IllegalArgumentException(
                            "Step '%s' depends on unknown step '%s'".formatted(entry.getKey(), dependency));
                }
                if (entry.getKey().equals(dependency)) {
                    throw new IllegalArgumentException("Step '%s' cannot depend on itself".formatted(entry.getKey()));
                }
            }
        }

        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, Set<String>> dependents = new HashMap<>();
        dependencies.keySet().forEach(step -> {
            inDegree.put(step, 0);
            dependents.put(step, new HashSet<>());
        });
        dependencies.forEach((step, requirements) -> requirements.forEach(requirement -> {
            if (dependents.get(requirement).add(step)) {
                inDegree.compute(step, (ignored, degree) -> degree + 1);
            }
        }));

        ArrayDeque<String> ready = new ArrayDeque<>();
        inDegree.forEach((step, degree) -> {
            if (degree == 0) {
                ready.add(step);
            }
        });

        int visited = 0;
        while (!ready.isEmpty()) {
            String step = ready.removeFirst();
            visited++;
            for (String dependent : dependents.get(step)) {
                int degree = inDegree.compute(dependent, (ignored, value) -> value - 1);
                if (degree == 0) {
                    ready.add(dependent);
                }
            }
        }

        if (visited != dependencies.size()) {
            throw new IllegalArgumentException("Workflow steps must form an acyclic graph");
        }
    }
}
