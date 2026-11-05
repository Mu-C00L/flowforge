package io.flowforge.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.flowforge.api.ApiException;
import io.flowforge.domain.StepDefinition;
import io.flowforge.domain.StoredDefinition;
import io.flowforge.domain.WorkflowDefinitionSpec;
import io.flowforge.domain.WorkflowEvent;
import io.flowforge.domain.WorkflowSnapshot;
import io.flowforge.handler.StepHandlerRegistry;
import io.flowforge.repository.DefinitionRepository;
import io.flowforge.repository.WorkflowRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

@Service
public class WorkflowService {
    private final DefinitionRepository definitions;
    private final WorkflowRepository workflows;
    private final DefinitionValidator validator;
    private final StepHandlerRegistry handlers;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;

    public WorkflowService(
            DefinitionRepository definitions,
            WorkflowRepository workflows,
            DefinitionValidator validator,
            StepHandlerRegistry handlers,
            TransactionTemplate transactions,
            ObjectMapper objectMapper
    ) {
        this.definitions = definitions;
        this.workflows = workflows;
        this.validator = validator;
        this.handlers = handlers;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
    }

    public StoredDefinition createDefinition(WorkflowDefinitionSpec spec) {
        validator.validate(spec);
        List<String> unknownHandlers = spec.steps().stream()
                .map(StepDefinition::handler)
                .distinct()
                .filter(handler -> !handlers.contains(handler))
                .toList();
        if (!unknownHandlers.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown handlers: " + unknownHandlers);
        }
        try {
            return definitions.create(spec);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Definition '%s' version %d already exists".formatted(spec.name(), spec.version()));
        }
    }

    public WorkflowSnapshot start(
            String definitionName,
            int definitionVersion,
            JsonNode input,
            String idempotencyKey
    ) {
        if (idempotencyKey != null && idempotencyKey.length() > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be at most 200 characters");
        }
        if (idempotencyKey != null) {
            UUID existing = workflows.findByIdempotencyKey(idempotencyKey).orElse(null);
            if (existing != null) {
                return get(existing);
            }
        }

        StoredDefinition definition = definitions.find(definitionName, definitionVersion)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Workflow definition not found: %s:%d".formatted(definitionName, definitionVersion)));
        UUID workflowId = UUID.randomUUID();
        JsonNode safeInput = input == null ? objectMapper.createObjectNode() : input;

        try {
            transactions.executeWithoutResult(status -> {
                workflows.insertWorkflow(workflowId, definition.id(), safeInput, idempotencyKey);
                definition.spec().steps().forEach(step -> workflows.insertTask(workflowId, step, safeInput));
                workflows.appendEvent(workflowId, null, "WORKFLOW_STARTED", objectMapper.createObjectNode());
            });
        } catch (DataIntegrityViolationException exception) {
            if (idempotencyKey != null) {
                return workflows.findByIdempotencyKey(idempotencyKey)
                        .map(this::get)
                        .orElseThrow(() -> exception);
            }
            throw exception;
        }
        return get(workflowId);
    }

    public WorkflowSnapshot get(UUID workflowId) {
        return workflows.findSnapshot(workflowId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Workflow not found: " + workflowId));
    }

    public List<WorkflowEvent> events(UUID workflowId) {
        get(workflowId);
        return workflows.listEvents(workflowId);
    }

    public WorkflowSnapshot cancel(UUID workflowId) {
        get(workflowId);
        if (workflows.cancel(workflowId)) {
            workflows.appendEvent(workflowId, null, "WORKFLOW_CANCELED", objectMapper.createObjectNode());
        }
        return get(workflowId);
    }

    public WorkflowSnapshot retry(UUID workflowId) {
        WorkflowSnapshot snapshot = get(workflowId);
        if (snapshot.status() != io.flowforge.domain.WorkflowStatus.FAILED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only failed workflows can be retried; current status: " + snapshot.status());
        }
        if (workflows.retryFailed(workflowId)) {
            workflows.appendEvent(workflowId, null, "WORKFLOW_RETRY_REQUESTED", objectMapper.createObjectNode());
        }
        return get(workflowId);
    }
}
