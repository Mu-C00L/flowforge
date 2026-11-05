-- Migration: Add index to support efficient cleanup and queries on terminal workflows and events
CREATE INDEX IF NOT EXISTS idx_workflow_instances_status_completed
    ON workflow_instances (status, completed_at)
    WHERE status IN ('SUCCEEDED', 'FAILED', 'CANCELED');

CREATE INDEX IF NOT EXISTS idx_workflow_events_occurred_at
    ON workflow_events (occurred_at);
