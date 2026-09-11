# Insurance Policy Renewal System

A Spring Boot + Spring Batch system that automatically detects expiring insurance policies, sends renewal reminders, retries failed sends with exponential backoff, dead-letters permanently-failing reminders, lapses unrenewed policies after a grace period, and produces a daily funnel report.

No message broker (Kafka/RabbitMQ) is used. The `reminder_outbox` table implements the **transactional outbox pattern** instead — Job 1 writes to it inside the same database transaction as the policy status update; the Notification Poller reads from it using `SELECT ... FOR UPDATE SKIP LOCKED` to safely support multiple concurrent workers without a broker.

Batch job schedules are stored in the `job_schedule` database table and can be changed at runtime via the `/schedules` API without restarting the app.

---

## Prerequisites

- Java 17
- Maven 3.9+
- No external database — uses H2 in file mode (`./data/policyrenewaldb`), created automatically on first run

---

## Tech Stack

| Concern | Choice |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.3.5 |
| Batch processing | Spring Batch |
| Data access | Spring Data JPA (Hibernate) with custom @Query methods |
| Database | H2, file-based (persists across restarts) |
| Schema management | `schema.sql` (not Flyway/Liquibase) |
| Build | Maven |
| Boilerplate | Lombok |

---

## Build

```bash
mvn clean install
```

Compiles, runs all 3 tests, packages the runnable JAR.

## Run

```bash
mvn spring-boot:run
```
or
```bash
java -jar target/policy-renewal-system-0.0.1-SNAPSHOT.jar
```

App starts on **http://localhost:8080**. On first startup it creates `./data/policyrenewaldb.mv.db` and runs `schema.sql` which creates all 5 tables and seeds the default job schedules.

**H2 Console**: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:file:./data/policyrenewaldb`
- Username: `sa`, Password: *(blank)*

**Logs**: written to `logs/policy-renewal-system.log` with daily rolling (max 10MB per file, 7 days history, 100MB total cap).

---

## What runs automatically vs. manually

| Component | How it runs |
|---|---|
| **Notification Poller** | Automatic — `@Scheduled` every 5 seconds, continuously |
| **Job 1 — Detection** | Automatic via DB schedule (default 1am) **AND** manual via `POST /jobs/detection/run` |
| **Job 2 — Lapse** | Automatic via DB schedule (default 2am) **AND** manual via `POST /jobs/lapse/run` |
| **Funnel Report** | Automatic via DB schedule (default 3am) **AND** manual via `POST /jobs/report/run` |

Batch jobs are not auto-run on startup (`spring.batch.job.enabled: false`). The DB-driven scheduler registers them at startup and fires them at the stored cron times. Manual endpoints exist alongside for on-demand runs and demos.

---

## API Reference

### Policy

```
POST   /policies                        Create a policy (idempotent)
PUT    /policies/{policyNumber}/renew   Renew a policy
GET    /policies/{policyNumber}         Get policy with full reminder history
```

**Create body:**
```json
{
  "policyNumber": "POL-1001",
  "holderName":   "Ramesh Kumar",
  "email":        "ramesh@example.com",
  "policyType":   "MOTOR",
  "premiumAmount": 5000,
  "startDate":    "2025-09-01",
  "endDate":      "2026-09-25"
}
```
Returns `201 Created` for a new policy, `200 OK` with `"duplicate": true` if `policyNumber` already exists.

**Renew body:**
```json
{
  "paymentReference": "PAY-9988",
  "newEndDate": "2027-09-25"
}
```

### Batch job triggers (manual)
```
POST /jobs/detection/run    Run Job 1 immediately
POST /jobs/lapse/run        Run Job 2 immediately
POST /jobs/report/run       Generate the funnel report immediately
```

### Schedule management (DB-driven)
```
GET  /schedules                              View all job schedules from DB
PUT  /schedules/{jobName}/cron               Update cron expression (live, no restart)
PUT  /schedules/{jobName}/enabled            Enable or disable a job
```
**Update cron body:** `{ "cronExpr": "0 */2 * * * *" }`
**Enable/disable body:** `{ "enabled": false }`

Valid `{jobName}` values: `renewalDetectionJob`, `policyLapseJob`, `renewalFunnelReportJob`

### DLQ
```
GET  /dlq                   List dead-lettered reminders
POST /dlq/{id}/requeue      Requeue a DLQ entry back into the outbox
```

### Demo clock
```
GET  /demo/today                           Current simulated date
POST /demo/advance-clock?date=2026-10-15   Set simulated date
POST /demo/reset-clock                     Reset to real system time
```

---

## Demo Script — Full Lifecycle Without Waiting Real Days

### Setup
1. Start the app: `mvn spring-boot:run`
2. Confirm simulated date: `GET /demo/today` → should return today's real date
3. Open H2 console to watch tables: http://localhost:8080/h2-console

### Scenario 1 — Idempotent create
```
POST /policies  (body: POL-1001, endDate 25 days from today)
POST /policies  (same body again)
```
First call: `201 Created`, `"duplicate": false`
Second call: `200 OK`, `"duplicate": true` — proves idempotency

### Scenario 2 — Multiple reminder tiers fire correctly
```
POST /policies  POL-30DAY  endDate = today + 26 days  → 30_DAY tier
POST /policies  POL-15DAY  endDate = today + 13 days  → 15_DAY tier
POST /policies  POL-7DAY   endDate = today + 5 days   → 7_DAY tier
POST /policies  POL-OVER   endDate = today - 3 days   → OVERDUE tier
POST /jobs/detection/run
```
Check console log:
```
Job1 (Detection) summary: created[30_DAY]=1 created[15_DAY]=1 created[7_DAY]=1 created[OVERDUE]=1 skipped=0
```
Run detection again immediately:
```
POST /jobs/detection/run
```
Check console: `skipped=4` — proves unique-constraint duplicate prevention

### Scenario 3 — Notification outcomes
Wait ~10 seconds. Watch the console for the poller picking up reminders:
```
Reminder X sent (policy ..., tier ...)
Reminder X — invalid contact for policy ... Flagged for manual follow-up.
Reminder X timed out (attempt 1), next retry at ...
```
Since outcomes are random, all three may not appear immediately.
To force a DLQ entry quickly, temporarily set `notification.retry.max-attempts: 1` in `application.yaml`, restart, and create a new policy.

### Scenario 4 — Renewal cancels pending reminders
```
GET  /policies/POL-15DAY    → status: RENEWAL_DUE
PUT  /policies/POL-15DAY/renew  body: { "paymentReference": "PAY-001", "newEndDate": "2027-09-22" }
GET  /policies/POL-15DAY    → status: ACTIVE, reminderHistory shows attempts
```
Check `reminder_outbox` in H2 — any PENDING reminders for POL-15DAY now show CANCELLED.

### Scenario 5 — Multiple tiers over time
```
POST /demo/advance-clock?date=<today + 11 days>
POST /jobs/detection/run
```
POL-30DAY (was 26 days out, now 15 days out) should now get its 15_DAY reminder.
POL-15DAY is renewed already → ACTIVE → no new reminder.
Check outbox — new 15_DAY row for POL-30DAY. The 30_DAY row still there from before.

### Scenario 6 — Lapse job + grace period
```
POST /demo/advance-clock?date=<POL-OVER endDate + 16 days>
POST /jobs/lapse/run
GET  /policies/POL-OVER    → status: LAPSED
```
Console: `Job2 (Lapse) summary: lapsed=1 skipped=...`

### Scenario 7 — Dynamic scheduling
```
GET /schedules                                          → see all 3 jobs with their current cron
PUT /schedules/renewalDetectionJob/cron
    { "cronExpr": "0 */2 * * * *" }                    → fires every 2 minutes from now
```
Watch the console — detection job fires automatically every 2 minutes without you touching Postman.
```
PUT /schedules/renewalDetectionJob/enabled
    { "enabled": false }                                → pause it
PUT /schedules/renewalDetectionJob/cron
    { "cronExpr": "0 0 1 * * *" }                      → restore daily schedule
PUT /schedules/renewalDetectionJob/enabled
    { "enabled": true }                                 → re-enable
```

### Scenario 8 — DLQ view and requeue
```
GET  /dlq
POST /dlq/{id}/requeue
```

### Scenario 9 — Funnel report
```
POST /jobs/report/run
```
Check `reports/funnel-report-<date>.csv` in the project root.
Check console for the structured log summary.

### Scenario 10 — Reset
```
POST /demo/reset-clock
```

---

## Restartability

Spring Batch tracks progress per chunk in `BATCH_JOB_INSTANCE`, `BATCH_STEP_EXECUTION`, and related tables (auto-created via `spring.batch.jdbc.initialize-schema: always`). If the app is killed mid-job and restarted, Spring Batch resumes from the last committed chunk. Combined with the outbox unique constraint, even reprocessing a chunk is safe — it just skips as "duplicate." To demonstrate: kill the app mid-job with Ctrl+C, restart, run the same job again — check `BATCH_STEP_EXECUTION` in H2 to see the previous (FAILED) and new (COMPLETED) executions.

---

## Tests

```bash
mvn test
```

| Test | File | What it proves |
|---|---|---|
| Idempotent create | `PolicyServiceTest` | Second `POST /policies` with same number returns `duplicate: true`, never calls `save()` |
| No duplicate reminders on rerun | `ReminderServiceIntegrationTest` | Calling `createReminderIfNeeded` twice returns `SKIPPED_DUPLICATE`, only 1 outbox row exists |
| Retry-to-DLQ | `NotificationServiceTest` | Gateway always times out, `maxAttempts=1`, outbox status becomes `FAILED`, DLQ row created |

The integration test uses `application-test.yml` (in-memory `jdbc:h2:mem:...`) so it never touches the real `./data/policyrenewaldb` file.

---

## Configuration Reference

All knobs live in `src/main/resources/application.yaml`:

```yaml
renewal:
  detection:
    window-days: 30        # how far ahead Job 1 looks for expiring policies
    chunk-size: 10
  lapse:
    grace-period-days: 15  # days after endDate before a policy can lapse
    chunk-size: 10

notification:
  poll:
    interval-ms: 5000      # poller wake-up frequency (ms)
    batch-size: 5          # rows per worker per pick-up
    worker-count: 3        # parallel workers per wake-up
  pool:
    core-size: 3
    max-size: 5
    queue-capacity: 50
  retry:
    max-attempts: 3        # timeouts before dead-lettering
    base-delay-minutes: 1  # exponential backoff base unit

report:
  output-dir: reports      # where funnel-report CSVs are written
```

Batch job cron schedules are NOT in this file — they live in the `job_schedule` database table, seeded by `schema.sql` and modifiable at runtime via `PUT /schedules/{jobName}/cron`.

---

## Project Structure

```
com.fulcrumdigital.policyrenewal
├── batch/
│   ├── detection/     Job 1 (Detection) — config, listener, stats, carrier class
│   ├── lapse/         Job 2 (Lapse) — config, listener, stats
│   ├── report/        Report job — tasklet config
│   └── scheduler/     Dynamic DB-driven scheduler (SchedulingConfigurer)
├── config/            ClockConfig (delegating clock), ExecutorConfig (thread pools)
├── controller/        PolicyController, BatchJobController, DlqController,
│                      JobScheduleController, DemoController
├── dto/               Request/response shapes (CreatePolicyRequest, PolicyResponse, ...)
├── entity/
│   ├── enums/         PolicyStatus, PolicyType, ReminderTier, NotificationOutcome
│   ├── Policy.java    JPA entities (1:1 with DB tables)
│   ├── ReminderOutbox.java
│   ├── NotificationLog.java
│   ├── ReminderDlq.java
│   └── JobSchedule.java
├── exception/         Custom exceptions + GlobalExceptionHandler
├── notification/      NotificationPoller (@Scheduled), GatewaySimulator, BackoffCalculator
├── repository/        Spring Data JPA interfaces (all custom @Query methods, no auto-generated queries)
└── service/           PolicyService, PolicyRenewalExecutor, ReminderService,
                       ReminderOutboxWriter, ReminderTierCalculator,
                       NotificationService, ReportService, JobScheduleService
```

See `docs/HLD-LLD-Document.md` for full architecture, ER diagram, sequence diagrams, and transaction boundary rationale.
