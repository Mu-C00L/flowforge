package io.flowforge.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flowforge.domain.StepDefinition;
import io.flowforge.domain.TaskStatus;
import io.flowforge.domain.WorkflowDefinitionSpec;
import io.flowforge.domain.WorkflowSnapshot;
import io.flowforge.domain.WorkflowStatus;
import io.flowforge.engine.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "flowforge.worker.poll-interval-ms=25",
        "flowforge.worker.recovery-interval-ms=100"
})
@Testcontainers(disabledWithoutDocker = true)
class WorkflowExecutionIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    WorkflowService workflows;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void executesDagRetriesPostCommitFailureAndReusesIdempotencyKey() throws Exception {
        WorkflowDefinitionSpec definition = new WorkflowDefinitionSpec(
                "integration-order", 1, List.of(
                new StepDefinition("reserve", "idempotent-operation", List.of(), 3,
                        Map.of("operation", "reserve-inventory",
                                "simulatePostCommitFailureOnAttempt", 1)),
                new StepDefinition("charge", "idempotent-operation", List.of("reserve"), 3,
                        Map.of("operation", "charge-payment")),
                new StepDefinition("audit", "echo", List.of("reserve"), 2, Map.of()),
                new StepDefinition("ship", "idempotent-operation", List.of("charge", "audit"), 3,
                        Map.of("operation", "arrange-shipment"))));
        workflows.createDefinition(definition);

        String idempotencyKey = "integration-order-42";
        WorkflowSnapshot started = workflows.start(
                definition.name(), definition.version(),
                objectMapper.readTree("{\"orderId\":\"order-42\"}"), idempotencyKey);
        WorkflowSnapshot duplicate = workflows.start(
                definition.name(), definition.version(),
                objectMapper.readTree("{\"orderId\":\"different\"}"), idempotencyKey);
        assertThat(duplicate.id()).isEqualTo(started.id());

        WorkflowSnapshot completed = awaitTerminal(started);
        assertThat(completed.status()).isEqualTo(WorkflowStatus.SUCCEEDED);
        assertThat(completed.tasks()).allMatch(task -> task.status() == TaskStatus.SUCCEEDED);
        assertThat(completed.tasks().stream()
                .filter(task -> task.stepId().equals("reserve"))
                .findFirst().orElseThrow().attempts()).isEqualTo(2);
        assertThat(completed.output().path("reserve").path("committedByAttempt").asInt()).isEqualTo(1);
    }

    private WorkflowSnapshot awaitTerminal(WorkflowSnapshot started) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        WorkflowSnapshot snapshot = started;
        while (snapshot.status() == WorkflowStatus.RUNNING && Instant.now().isBefore(deadline)) {
            Thread.sleep(50);
            snapshot = workflows.get(started.id());
        }
        return snapshot;
    }
}
