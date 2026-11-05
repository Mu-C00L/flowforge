# Roadmap

## Milestone 1 — durable single-region engine

- [x] Versioned DAG definitions
- [x] Transactional task claims with `SKIP LOCKED`
- [x] Parallel branches and dependency joins
- [x] Exponential retry and terminal failure
- [x] Worker leases and crash recovery
- [x] Idempotent workflow starts
- [x] Append-only execution history
- [x] Prometheus metrics
- [x] Unit and PostgreSQL integration tests

## Milestone 2 — production operability

- [x] Lease heartbeats for long-running tasks
- [x] Per-step output data piping to dependent tasks
- [x] Jittered exponential backoff policy
- [x] Manual retry API for failed workflows
- [x] Indexing for retention and terminal workflow cleanup
- [ ] OpenTelemetry spans across API, scheduler, and handlers
- [ ] Per-workflow retry and timeout policies
- [ ] API authentication and tenant-scoped RBAC
- [ ] Outbox-based Kafka notifications
- [ ] Grafana dashboard and alert rules

## Milestone 3 — product surface

- [ ] React execution timeline and DAG visualization
- [ ] Java worker SDK for externally hosted handlers
- [ ] Cron schedules and delayed workflow starts
- [ ] Search, pagination, and retention controls
- [ ] Load generator and published benchmark report
