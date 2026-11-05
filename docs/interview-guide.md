# Interview guide

## Why not Kafka first?

Kafka is excellent for notification and fan-out, but a durable workflow engine still needs authoritative state for dependencies, attempts, leases, cancellation, and history. PostgreSQL lets the MVP atomically claim a task and update its ownership. A later Kafka design should use a transactional outbox and a reconciliation loop rather than a fragile database-plus-broker dual write.

## Is execution exactly once?

No. Exactly-once external side effects cannot be guaranteed by this engine alone. The engine provides at-least-once task execution. Handlers must make effects idempotent using the stable task ID, or the downstream system must support conditional writes or idempotency keys.

The example handler records a result keyed by task ID, deliberately throws after the first commit, and then returns the original result on retry.

## Why use leases?

A permanent `RUNNING` state would strand work whenever a process dies. A time-bounded lease allows another worker to recover it. Completion is conditional on the recorded owner so an expired worker cannot overwrite a newer attempt.

For tasks longer than the lease, add periodic heartbeats. Heartbeats should extend only leases still owned by that worker and should stop before task completion is committed.

## What can become a bottleneck?

- The partial pending-task index receives frequent updates.
- Polling creates database load while the queue is empty.
- Advancing a DAG currently reloads task statuses after each success.
- One PostgreSQL primary is the availability boundary.
- Event history grows without retention or partitioning.

Mitigations include adaptive polling, notification-assisted wakeups, partitioning by tenant or time, cached dependency counters, event archival, and eventually Kafka-based task-ready notification with PostgreSQL reconciliation.

## What happens during cancellation?

Blocked, pending, and running tasks are marked canceled. A handler already performing an external action cannot be forcibly rolled back. Its eventual success update is rejected because the task is no longer `RUNNING`. Business-level cancellation therefore requires explicit compensation steps, not only engine state changes.

## What would you benchmark?

- Claim-to-start scheduling latency at p50, p95, and p99
- Completed tasks per second as worker count grows
- Database CPU, lock wait time, and connection saturation
- Empty-queue polling cost
- Lease-recovery latency after killing workers
- Performance with shallow-wide versus deep-narrow DAGs

Publish the workload generator, environment, raw results, and exact commit. Avoid claiming production-scale performance from an unrepeatable laptop test.
