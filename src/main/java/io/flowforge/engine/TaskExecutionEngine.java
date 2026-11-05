package io.flowforge.engine;

import com.fasterxml.jackson.databind.JsonNode;
import io.flowforge.domain.ClaimedTask;
import io.flowforge.handler.StepExecutionContext;
import io.flowforge.handler.StepHandler;
import io.flowforge.handler.StepHandlerRegistry;
import io.flowforge.repository.TaskRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class TaskExecutionEngine {
    private static final Logger log = LoggerFactory.getLogger(TaskExecutionEngine.class);

    private final StepHandlerRegistry handlers;
    private final ExecutionCoordinator coordinator;
    private final MeterRegistry metrics;
    private final TaskRepository tasks;

    public TaskExecutionEngine(
            StepHandlerRegistry handlers,
            ExecutionCoordinator coordinator,
            MeterRegistry metrics,
            TaskRepository tasks
    ) {
        this.handlers = handlers;
        this.coordinator = coordinator;
        this.metrics = metrics;
        this.tasks = tasks;
    }

    public void execute(ClaimedTask task, String workerId) {
        try (MDC.MDCCloseable ignoredWorkflow = MDC.putCloseable("workflowId", task.workflowInstanceId().toString());
             MDC.MDCCloseable ignoredTask = MDC.putCloseable("taskId", task.id().toString())) {
            Timer.Sample timer = Timer.start(metrics);
            try {
                StepHandler handler = handlers.require(task.handler());
                StepExecutionContext context = new StepExecutionContext(
                        task.workflowInstanceId(), task.id(), task.stepId(), task.attempt(),
                        task.input(), task.config(),
                        taskId -> {
                            boolean renewed = tasks.heartbeat(taskId, workerId, java.time.Duration.ofSeconds(30));
                            if (!renewed) {
                                log.warn("Heartbeat failed to renew lease for task {} (worker {})", taskId, workerId);
                            }
                        });
                JsonNode output = handler.execute(context);
                if (coordinator.succeed(task, workerId, output)) {
                    counter("flowforge.tasks.succeeded", task.handler()).increment();
                }
            } catch (Throwable failure) {
                log.warn("Task execution failed on attempt {}", task.attempt(), failure);
                if (coordinator.fail(task, workerId, failure)) {
                    counter("flowforge.tasks.failed_attempts", task.handler()).increment();
                }
            } finally {
                timer.stop(Timer.builder("flowforge.tasks.duration")
                        .tag("handler", task.handler())
                        .register(metrics));
            }
        }
    }

    private Counter counter(String name, String handler) {
        return Counter.builder(name).tag("handler", handler).register(metrics);
    }
}
