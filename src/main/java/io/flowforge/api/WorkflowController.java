package io.flowforge.api;

import io.flowforge.domain.WorkflowEvent;
import io.flowforge.domain.WorkflowSnapshot;
import io.flowforge.engine.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {
    private final WorkflowService workflows;

    public WorkflowController(WorkflowService workflows) {
        this.workflows = workflows;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowSnapshot start(
            @Valid @RequestBody StartWorkflowRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return workflows.start(
                request.definitionName(), request.definitionVersion(), request.input(), idempotencyKey);
    }

    @GetMapping("/{workflowId}")
    public WorkflowSnapshot get(@PathVariable UUID workflowId) {
        return workflows.get(workflowId);
    }

    @GetMapping("/{workflowId}/events")
    public List<WorkflowEvent> events(@PathVariable UUID workflowId) {
        return workflows.events(workflowId);
    }

    @PostMapping("/{workflowId}/cancel")
    public WorkflowSnapshot cancel(@PathVariable UUID workflowId) {
        return workflows.cancel(workflowId);
    }

    @PostMapping("/{workflowId}/retry")
    public WorkflowSnapshot retry(@PathVariable UUID workflowId) {
        return workflows.retry(workflowId);
    }
}
