CREATE TABLE workflow_definitions (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    version INTEGER NOT NULL CHECK (version > 0),
    definition_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_workflow_definition_name_version UNIQUE (name, version)
);

CREATE TABLE workflow_instances (
    id UUID PRIMARY KEY,
    definition_id UUID NOT NULL REFERENCES workflow_definitions(id),
    status VARCHAR(24) NOT NULL,
    input_json JSONB NOT NULL,
    output_json JSONB,
    idempotency_key VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_workflow_idempotency_key UNIQUE (idempotency_key)
);

CREATE TABLE workflow_tasks (
    id UUID PRIMARY KEY,
    workflow_instance_id UUID NOT NULL REFERENCES workflow_instances(id) ON DELETE CASCADE,
    step_id VARCHAR(120) NOT NULL,
    handler VARCHAR(160) NOT NULL,
    status VARCHAR(24) NOT NULL,
    input_json JSONB NOT NULL,
    config_json JSONB NOT NULL,
    output_json JSONB,
    error_message TEXT,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    max_attempts INTEGER NOT NULL CHECK (max_attempts BETWEEN 1 AND 20),
    available_at TIMESTAMPTZ,
    lease_owner VARCHAR(200),
    lease_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_workflow_step UNIQUE (workflow_instance_id, step_id)
);

CREATE INDEX idx_tasks_claim
    ON workflow_tasks (available_at, created_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_tasks_workflow ON workflow_tasks (workflow_instance_id);
CREATE INDEX idx_tasks_expired_leases
    ON workflow_tasks (lease_expires_at)
    WHERE status = 'RUNNING';

CREATE TABLE workflow_events (
    sequence_id BIGSERIAL PRIMARY KEY,
    workflow_instance_id UUID NOT NULL REFERENCES workflow_instances(id) ON DELETE CASCADE,
    task_id UUID REFERENCES workflow_tasks(id) ON DELETE CASCADE,
    event_type VARCHAR(80) NOT NULL,
    details_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_workflow
    ON workflow_events (workflow_instance_id, sequence_id);
