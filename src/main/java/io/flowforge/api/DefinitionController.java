package io.flowforge.api;

import io.flowforge.domain.StoredDefinition;
import io.flowforge.domain.WorkflowDefinitionSpec;
import io.flowforge.engine.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/definitions")
public class DefinitionController {
    private final WorkflowService workflows;

    public DefinitionController(WorkflowService workflows) {
        this.workflows = workflows;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoredDefinition create(@Valid @RequestBody WorkflowDefinitionSpec definition) {
        return workflows.createDefinition(definition);
    }
}
