CREATE TABLE handler_execution_ledger (
    task_id UUID PRIMARY KEY REFERENCES workflow_tasks(id) ON DELETE CASCADE,
    operation VARCHAR(160) NOT NULL,
    result_json JSONB NOT NULL,
    committed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
