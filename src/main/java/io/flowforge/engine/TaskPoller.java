package io.flowforge.engine;

import io.flowforge.repository.TaskRepository;
import io.flowforge.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Semaphore;

@Component
public class TaskPoller {
    private static final Logger log = LoggerFactory.getLogger(TaskPoller.class);

    private final TaskRepository tasks;
    private final TaskExecutionEngine executionEngine;
    private final WorkflowRepository workflows;
    private final ObjectMapper objectMapper;
    private final TaskExecutor executor;
    private final MeterRegistry metrics;
    private final Semaphore capacity;
    private final Duration leaseDuration;
    private final String workerId;

    public TaskPoller(
            TaskRepository tasks,
            TaskExecutionEngine executionEngine,
            WorkflowRepository workflows,
            ObjectMapper objectMapper,
            @Qualifier("workerTaskExecutor") TaskExecutor executor,
            MeterRegistry metrics,
            @Value("${flowforge.worker.concurrency:4}") int concurrency,
            @Value("${flowforge.worker.lease-seconds:30}") long leaseSeconds
    ) {
        this.tasks = tasks;
        this.executionEngine = executionEngine;
        this.workflows = workflows;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.metrics = metrics;
        this.capacity = new Semaphore(concurrency);
        this.leaseDuration = Duration.ofSeconds(leaseSeconds);
        this.workerId = ManagementFactory.getRuntimeMXBean().getName() + "-" + UUID.randomUUID();
    }

    @Scheduled(fixedDelayString = "${flowforge.worker.poll-interval-ms:200}")
    public void poll() {
        if (!capacity.tryAcquire()) {
            return;
        }
        var claimed = tasks.claimNext(workerId, leaseDuration);
        if (claimed.isEmpty()) {
            capacity.release();
            return;
        }
        executor.execute(() -> {
            try {
                executionEngine.execute(claimed.get(), workerId);
            } finally {
                capacity.release();
            }
        });
    }

    @Scheduled(fixedDelayString = "${flowforge.worker.recovery-interval-ms:5000}")
    public void recoverExpiredLeases() {
        TaskRepository.RecoveryResult recovery = tasks.recoverExpiredLeases();
        if (recovery.requeuedTasks() > 0) {
            log.warn("Recovered {} task(s) with expired worker leases", recovery.requeuedTasks());
            metrics.counter("flowforge.tasks.recovered").increment(recovery.requeuedTasks());
        }
        for (UUID workflowId : recovery.failedWorkflows()) {
            var details = objectMapper.createObjectNode()
                    .put("error", "Worker lease expired after final attempt");
            workflows.appendEvent(workflowId, null, "WORKFLOW_FAILED", details);
            metrics.counter("flowforge.workflows.failed_after_lease_expiry").increment();
        }
    }
}
