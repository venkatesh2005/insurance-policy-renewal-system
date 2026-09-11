# Insurance Policy Renewal System — High & Low Level Design

---

## Part 1 — High Level Design

### 1.1 Architecture Overview

```
Client
  │
  ▼
PolicyController ──────────────────────────── PolicyService
BatchJobController                             ReminderService
DlqController                                  NotificationService
JobScheduleController                          ReportService
DemoController                                 JobScheduleService
                                               PolicyRenewalExecutor (internal)
                                               ReminderOutboxWriter (internal)
                                               ReminderTierCalculator
  │
  ▼
Repository Layer (all custom @Query — no auto-generated method-name queries)
  │
  ▼
H2 Database (file-based, persists across restarts)
  │
  ├── policies
  ├── reminder_outbox     ← transactional outbox (replaces Kafka/RabbitMQ)
  ├── notification_log
  ├── reminder_dlq
  └── job_schedule        ← DB-driven batch scheduling

Batch Layer (Spring Batch)
  ├── Job 1: renewalDetectionJob   (batch/detection/)
  ├── Job 2: policyLapseJob        (batch/lapse/)
  ├── Job 3: renewalFunnelReportJob (batch/report/)
  └── BatchJobScheduler             (batch/scheduler/) ← reads cron from job_schedule

Async Layer (Spring @Scheduled)
  └── NotificationPoller — fires every 5s, picks up pending outbox rows
```

### 1.2 Component Responsibilities

| Component | Responsible for |
|---|---|
| **Policy API** | Create (idempotent), renew (cancels pending reminders + retry on lock conflict), fetch with history |
| **Job 1 — Detection** | Scans ACTIVE + RENEWAL_DUE policies expiring within window; assigns tier; writes to outbox |
| **Notification Poller** | Picks up outbox rows, simulates sending, routes SUCCESS/INVALID_CONTACT/TIMEOUT; retries with backoff; dead-letters after 3 failures |
| **Job 2 — Lapse** | Scans RENEWAL_DUE policies past grace period; lapses them; handles renew-vs-lapse race |
| **Report Job** | Aggregates policy status counts and notification outcomes into CSV + structured log |
| **DLQ API** | Lists dead-lettered reminders; requeues them back into the outbox |
| **Schedule API** | Reads/updates job cron expressions and enabled flags from the database; applies changes live |
| **Demo Clock** | Advances/resets the app-wide simulated date without touching business logic |

### 1.3 Policy Lifecycle

```
[POST /policies]
      │
      ▼
   ACTIVE
      │
      │ Job 1 detects endDate within 30 days
      ▼
RENEWAL_DUE ──────────────────────────────────────── [renew API + payment]
      │                                                       │
      │ Job 1 still scans RENEWAL_DUE for later tiers         │
      │ (15_DAY, 7_DAY, OVERDUE)                             │
      │                                                       ▼
      │ Job 2: past endDate + grace period             ACTIVE (again)
      ▼
   LAPSED ──── [renew API still works] ──────────────── ACTIVE (again)
```

### 1.4 Reminder Event Lifecycle

```
[Job 1 creates outbox row]
        │
        ▼
     PENDING
     /  |  \
    /   |   \
SUCCESS INVALID  TIMEOUT
   |    CONTACT     |
   ▼       |        ├── attempt < maxAttempts → retryCount++, nextRetryAt = backoff → PENDING (retry)
  SENT      |        └── attempt >= maxAttempts → FAILED + DLQ row
            ▼
          FAILED (no retry — bad contact, flag policy.needsManualFollowUp)
  
[renew API called while PENDING]
        │
        ▼
   CANCELLED (bulk UPDATE, never sent)
   
[DLQ requeue]
        │
        ▼
   PENDING (fresh row, retryCount=0)
```

### 1.5 Failure Scenarios and How They Are Handled

| Scenario | Mechanism |
|---|---|
| Job 1 runs twice on the same day | `UNIQUE(policy_number, tier, reminder_date)` on `reminder_outbox` rejects the duplicate at DB level; caught in `ReminderOutboxWriter`, returns `SKIPPED_DUPLICATE` |
| App crashes mid-batch-job | Spring Batch chunk tracking in `BATCH_STEP_EXECUTION` allows resume from the last committed chunk on restart |
| Two poller workers grab the same row | `SELECT ... FOR UPDATE SKIP LOCKED` — each worker sees only rows no other transaction holds |
| Reminder mid-retry when customer renews | `cancelPendingReminders` bulk UPDATE flips all PENDING rows to CANCELLED in the same transaction as the renewal; poller also re-checks `status == PENDING` before sending |
| Renewal and lapse race on the same policy | (1) `findLapseCandidates` uses `SELECT ... FOR UPDATE` — locks the row while the lapse job processes it; (2) `@Version` on `Policy` catches concurrent updates via `ObjectOptimisticLockingFailureException`; `renewPolicy` catches this and retries once against the fresh row |
| Schedule change without restart | `BatchJobScheduler.reschedule()` re-registers the job with the new `CronTrigger` immediately after the DB update |

---

## Part 2 — Low Level Design

### 2.1 Database Schema (ER Diagram)

```
POLICIES ||--o{ REMINDER_OUTBOX : has
REMINDER_OUTBOX ||--o{ NOTIFICATION_LOG : logs
REMINDER_OUTBOX |o--o| REMINDER_DLQ : escalates_to

POLICIES {
  bigint  id            PK
  string  policy_number UK  ← UNIQUE: idempotent create guaranteed at DB level
  string  holder_name
  string  email
  string  mobile
  string  policy_type       ← CHECK: HEALTH/MOTOR/TERM
  decimal premium_amount    ← CHECK: > 0
  date    start_date
  date    end_date          ← CHECK: > start_date
  string  status            ← CHECK: ACTIVE/RENEWAL_DUE/LAPSED
  boolean needs_manual_follow_up
  bigint  version           ← @Version: optimistic locking for renew-vs-lapse race
  date    last_renewed_at   ← funnel report "RENEWED_TODAY" count
}

REMINDER_OUTBOX {
  bigint    id              PK
  string    policy_number   FK → policies
  string    tier                ← 30_DAY/15_DAY/7_DAY/OVERDUE
  string    channel             ← EMAIL/SMS
  string    payload
  string    status              ← PENDING/SENT/FAILED/CANCELLED
  int       retry_count
  timestamp next_retry_at       ← backoff target; poller checks this before pickup
  date      reminder_date   ← part of UNIQUE(policy_number, tier, reminder_date)
}
UNIQUE(policy_number, tier, reminder_date) ← THE core no-duplicate guarantee

NOTIFICATION_LOG {
  bigint    id              PK
  bigint    outbox_id       FK → reminder_outbox
  string    policy_number
  string    tier
  string    channel
  int       attempt_number      ← 1, 2, 3 — one row per attempt, not per reminder
  string    outcome             ← SUCCESS/INVALID_CONTACT/GATEWAY_TIMEOUT
  string    error_message
  timestamp attempted_at
}

REMINDER_DLQ {
  bigint    id              PK
  bigint    outbox_id           ← pointer back for requeueing
  string    policy_number
  string    tier
  string    channel
  string    payload             ← preserved so requeue doesn't need to regenerate
  int       retry_count
  string    last_error
  timestamp moved_at
}

JOB_SCHEDULE {
  bigint    id              PK
  string    job_name        UK  ← must match Spring Batch Job bean name exactly
  string    cron_expr           ← 6-field Spring cron; read at startup, updatable at runtime
  boolean   enabled             ← false = not registered in scheduler at all
  string    description         ← human-readable note
  timestamp updated_at          ← auto-updated by Hibernate
}
```

### 2.2 Key Indexes

| Table | Index | Why |
|---|---|---|
| `policies` | `(status, end_date)` | Job 1 and Job 2 scan conditions |
| `reminder_outbox` | `(status, next_retry_at)` | Poller pickup query |
| `reminder_outbox` | `policy_number` | Cancel-by-policy bulk UPDATE |
| `notification_log` | `policy_number` | GET /policies history lookup |
| `notification_log` | `outbox_id` | Per-attempt tracing |
| `reminder_dlq` | `policy_number` | DLQ lookup by policy |

### 2.3 Class-Level Design

**Repository layer — all custom `@Query` methods (no auto-generated queries):**

| Repository | Key methods |
|---|---|
| `PolicyRepository` | `findByPolicyNumber`, `existsByPolicyNumber`, `findActivePoliciesForDetection(statuses, cutoff)`, `findLapseCandidates(cutoff)` [PESSIMISTIC_WRITE], `countPoliciesByStatus`, `countPoliciesRenewedOn` |
| `ReminderOutboxRepository` | `findByPolicyNumber`, `findByPolicyNumberAndStatus`, `findPendingBatchForUpdate` [native, SKIP LOCKED], `cancelPendingReminders` [@Modifying bulk UPDATE] |
| `NotificationLogRepository` | `findByPolicyNumber`, `countByTierAndOutcome` |
| `ReminderDlqRepository` | `findByPolicyNumber`, `countByTier` |
| `JobScheduleRepository` | `findByJobName`, `findAllEnabled` |

**Service layer — notable design decisions:**

`PolicyRenewalExecutor` — a separate package-private bean (not a method inside `PolicyService`) so `renewPolicy`'s retry-on-`ObjectOptimisticLockingFailureException` goes through Spring's transaction proxy. Calling `@Transactional` on `this` from within the same class (self-invocation) bypasses the proxy entirely and silently makes the transaction annotation do nothing.

`ReminderOutboxWriter` — a separate package-private bean with `@Transactional(REQUIRES_NEW)` so that a duplicate-constraint violation rolls back only the one insert, not the surrounding `ReminderService` transaction (which also holds the policy status update). Without isolation, Hibernate marks the whole outer transaction rollback-only on any flush failure, even when the exception is caught.

`ReminderTierCalculator` — no batch dependency, pure date math → lives in `service/` not `batch/`.

**Batch layer:**
- Detection, Lapse, Report each have their own sub-package (`batch/detection/`, `batch/lapse/`, `batch/report/`)
- `batch/scheduler/BatchJobScheduler` implements `SchedulingConfigurer` (not `@Scheduled`) so cron expressions are read from the DB at startup and can be changed at runtime via `reschedule(jobName, newCronExpr)`

### 2.4 Sequence Diagram — Happy Path

```
User                    Job1(Detection)         DB              Poller          Gateway
 │                            │                  │                │                │
 │ POST /jobs/detection/run   │                  │                │                │
 │──────────────────────────►│                  │                │                │
 │                            │ SELECT policies  │                │                │
 │                            │ (ACTIVE+RENEWAL_DUE, endDate<=cutoff)             │
 │                            │─────────────────►│                │                │
 │                            │◄─────────────────│                │                │
 │                            │ UPDATE policy→RENEWAL_DUE         │                │
 │                            │ INSERT reminder_outbox(PENDING)    │                │
 │                            │─────────────────►│                │                │
 │ "Detection job triggered"  │                  │                │                │
 │◄──────────────────────────│                  │                │                │
 │                            │                  │                │                │
 │                            │         (5 seconds pass)          │                │
 │                            │                  │                │                │
 │                            │                  │ SELECT...SKIP  │                │
 │                            │                  │ LOCKED (batch) │                │
 │                            │                  │◄───────────────│                │
 │                            │                  │────────────────►                │
 │                            │                  │                │ send(event)    │
 │                            │                  │                │───────────────►│
 │                            │                  │                │◄───────────────│
 │                            │                  │                │  SUCCESS        │
 │                            │                  │ UPDATE SENT    │                │
 │                            │                  │ INSERT log     │                │
 │                            │                  │◄───────────────│                │
```

### 2.5 Sequence Diagram — Retry Path to DLQ

```
Poller                  Gateway             DB
  │                        │                 │
  │ findPendingBatchForUpdate (attempt 1)    │
  │─────────────────────────────────────────►│ (rows locked)
  │◄─────────────────────────────────────────│
  │ send(event)            │                 │
  │───────────────────────►│                 │
  │ GATEWAY_TIMEOUT        │                 │
  │◄───────────────────────│                 │
  │ INSERT notification_log (attempt 1, TIMEOUT)
  │ UPDATE outbox: retryCount=1, nextRetryAt=now+1min
  │─────────────────────────────────────────►│
  │                (1 minute passes)          │
  │ findPendingBatchForUpdate (attempt 2)     │
  │ ... GATEWAY_TIMEOUT again ...             │
  │ INSERT log (attempt 2), retryCount=2, nextRetryAt=now+4min
  │─────────────────────────────────────────►│
  │                (4 minutes pass)           │
  │ findPendingBatchForUpdate (attempt 3)     │
  │ ... GATEWAY_TIMEOUT (attempt 3 = maxAttempts)
  │ INSERT log (attempt 3, TIMEOUT)           │
  │ UPDATE outbox: status=FAILED              │
  │ INSERT reminder_dlq                       │
  │─────────────────────────────────────────►│
```

### 2.6 Sequence Diagram — Renewal vs. Lapse Race

```
Job2(Lapse)             DB                  RenewAPI
     │                   │                      │
     │ SELECT...FOR UPDATE (locks policy row, version=3)
     │──────────────────►│                      │
     │◄──────────────────│                      │
     │                   │ ◄── PUT /policies/X/renew arrives
     │                   │     UPDATE → BLOCKS (row locked)
     │                   │                      │
     │ lapseIfStillEligible: status still RENEWAL_DUE → OK
     │ UPDATE status=LAPSED, version 3→4, COMMIT
     │──────────────────►│                      │
     │                   │ lock released         │
     │                   │ Renew UPDATE proceeds │
     │                   │ WHERE version=3 → 0 rows
     │                   │ ObjectOptimisticLockingFailureException
     │                   │                      │
     │                   │         PolicyService.renewPolicy catches it
     │                   │         re-fetches fresh row (version=4, LAPSED)
     │                   │         policyRenewalExecutor.renew(freshRow)
     │                   │         UPDATE status=ACTIVE, version 4→5, COMMIT
     │                   │◄─────────────────────│
     │                   │         Renewal succeeds — paying customer never sees an error
```

### 2.7 Transaction Boundaries

**Job 1 — policy status update + outbox insert must be in the same transaction:**
Both happen via `ReminderService.createReminderIfNeeded` (`@Transactional`). If they were in separate transactions and the app crashed between them, a policy could flip to `RENEWAL_DUE` with no reminder ever created. One transaction = either both commit or neither does.

**The outbox insert is further isolated in `ReminderOutboxWriter` with `REQUIRES_NEW`:**
A duplicate-constraint violation (the no-rerun guarantee) causes Hibernate to mark the *surrounding* transaction rollback-only, even when caught. `REQUIRES_NEW` ensures the violation only rolls back the insert, not the policy status update in the outer transaction. This separation requires a different bean (not `this`) to avoid self-invocation bypassing the proxy.

**Notification Poller — fetch + process in one transaction:**
`NotificationPoller.processBatch` is `@Transactional`, wrapping both `findPendingBatchForUpdate` and all processing. The `FOR UPDATE SKIP LOCKED` row lock is held for the entire duration — if the fetch committed before processing, a second worker could pick up the same row in the gap between fetch and process.

**`saveAndFlush` instead of `save` in `PolicyRenewalExecutor` and `lapseIfStillEligible`:**
A plain `save()` may defer the actual SQL `UPDATE` (and the optimistic-lock version check) to transaction-commit time — which is after these methods have returned and after their callers' `try/catch` blocks have exited. `saveAndFlush()` forces the check immediately, inside the method, where the exception can still be caught and acted on (retry for renewal, skip for lapse).
