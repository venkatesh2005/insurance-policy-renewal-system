# Insurance Policy Renewal System — Complete Demo Guide

**Stack:** Java 17 · Spring Boot 3.3.5 · Spring Batch · H2 · Maven
**Base URL:** http://localhost:8080

> All test data uses **relative dates** — calculated from TODAY when you run the
> script, so the demo works correctly on any date without any manual editing.

---

## Part 1 — Pre-Demo Setup

### 1.1 Start the app
```bash
mvn spring-boot:run
```
Wait for: `Started PolicyRenewalSystemApplication in X.XXX seconds`

### 1.2 Turn off SQL logging (cleaner console for the demo)
In `application.yaml`:
```yaml
jpa:
  show-sql: false
```
Restart once, leave it running.

### 1.3 Open H2 console in a browser tab
URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:file:./data/policyrenewaldb`
- Username: `sa` | Password: *(blank)*

### 1.4 Confirm simulated clock matches real date
```
GET http://localhost:8080/demo/today
Expected: today's real date (e.g. "2026-09-15" or whatever today is)
```

---

## Part 2 — Load All 20 Test Policies (Dynamic Dates)

### Option A — PowerShell script (Windows) — RECOMMENDED

Save as `load-demo-data.ps1` in your project root. It calculates every end date
relative to `[DateTime]::Today` so it works on any day you run it.

```powershell
$base = "http://localhost:8080/policies"
$h    = @{ "Content-Type" = "application/json" }
$today = [DateTime]::Today

function DateStr($daysOffset) {
    return $today.AddDays($daysOffset).ToString("yyyy-MM-dd")
}

# startDate is always 1 year before today
$start = $today.AddDays(-365).ToString("yyyy-MM-dd")

$policies = @(
    # --- Group 1: 30_DAY tier (end date 22-29 days from today) ---
    @{ policyNumber="POL-001"; holderName="Ramesh Kumar";   email="ramesh@example.com";    mobile="9876543210"; policyType="MOTOR";  premiumAmount=5000;  startDate=$start; endDate=DateStr(26) },
    @{ policyNumber="POL-002"; holderName="Aasha Patel";    email="aasha@example.com";     mobile="9123456780"; policyType="HEALTH"; premiumAmount=12000; startDate=$start; endDate=DateStr(28) },
    @{ policyNumber="POL-003"; holderName="Vijay Nair";     email="vijay@example.com";     mobile="9988776655"; policyType="TERM";   premiumAmount=8500;  startDate=$start; endDate=DateStr(22) },
    @{ policyNumber="POL-004"; holderName="Meena Krishnan";                                mobile="9444333222"; policyType="MOTOR";  premiumAmount=3200;  startDate=$start; endDate=DateStr(24) },

    # --- Group 2: 15_DAY tier (end date 8-14 days from today) ---
    @{ policyNumber="POL-005"; holderName="Karthik Raja";   email="karthik@example.com";   mobile="9000111222"; policyType="HEALTH"; premiumAmount=9500;  startDate=$start; endDate=DateStr(13) },
    @{ policyNumber="POL-006"; holderName="Saranya Devi";   email="saranya@example.com";   mobile="9111000333"; policyType="TERM";   premiumAmount=15000; startDate=$start; endDate=DateStr(11) },
    @{ policyNumber="POL-007"; holderName="Balu Sundaram";  email="balu@example.com";      mobile="9222111444"; policyType="MOTOR";  premiumAmount=4100;  startDate=$start; endDate=DateStr(9)  },
    @{ policyNumber="POL-008"; holderName="Deepa Mohan";                                   mobile="9333444555"; policyType="HEALTH"; premiumAmount=7800;  startDate=$start; endDate=DateStr(8)  },

    # --- Group 3: 7_DAY tier (end date 2-6 days from today) ---
    @{ policyNumber="POL-009"; holderName="Priya Menon";    email="priya@example.com";     mobile="9444555666"; policyType="TERM";   premiumAmount=20000; startDate=$start; endDate=DateStr(5)  },
    @{ policyNumber="POL-010"; holderName="Anbu Selvam";    email="anbu@example.com";      mobile="9555666777"; policyType="MOTOR";  premiumAmount=6200;  startDate=$start; endDate=DateStr(3)  },
    @{ policyNumber="POL-011"; holderName="Kavitha Rajan";  email="kavitha@example.com";   mobile="9666777888"; policyType="HEALTH"; premiumAmount=11000; startDate=$start; endDate=DateStr(2)  },

    # --- Group 4: OVERDUE tier (end date already past) ---
    @{ policyNumber="POL-012"; holderName="Suresh Babu";    email="suresh@example.com";    mobile="9777888999"; policyType="MOTOR";  premiumAmount=4800;  startDate=$start; endDate=DateStr(-3) },
    @{ policyNumber="POL-013"; holderName="Geetha Lakshmi"; email="geetha@example.com";    mobile="9888999000"; policyType="HEALTH"; premiumAmount=13500; startDate=$start; endDate=DateStr(-6) },
    @{ policyNumber="POL-014"; holderName="Murugan Raj";                                   mobile="9999000111"; policyType="TERM";   premiumAmount=25000; startDate=$start; endDate=DateStr(-8) },

    # --- Group 5: Will be RENEWED during demo ---
    @{ policyNumber="POL-015"; holderName="Lakshmi Priya";  email="lakshmi@example.com";   mobile="9101112131"; policyType="HEALTH"; premiumAmount=18000; startDate=$start; endDate=DateStr(6)  },
    @{ policyNumber="POL-016"; holderName="Dinesh Barath";  email="dinesh@example.com";    mobile="9141516171"; policyType="MOTOR";  premiumAmount=5500;  startDate=$start; endDate=DateStr(4)  },

    # --- Group 6: Will LAPSE (far past grace period) ---
    @{ policyNumber="POL-017"; holderName="Selvakumar T";   email="selva@example.com";     mobile="9181920212"; policyType="TERM";   premiumAmount=30000; startDate=$start; endDate=DateStr(-22) },
    @{ policyNumber="POL-018"; holderName="Revathi M";      email="revathi@example.com";   mobile="9222324252"; policyType="HEALTH"; premiumAmount=9000;  startDate=$start; endDate=DateStr(-27) },

    # --- Group 7: Far future — stays ACTIVE, skipped by Job 1 ---
    @{ policyNumber="POL-019"; holderName="Mani Iyer";      email="mani@example.com";      mobile="9262728293"; policyType="MOTOR";  premiumAmount=4400;  startDate=$start; endDate=DateStr(200) },
    @{ policyNumber="POL-020"; holderName="Santhiya R";     email="santhiya@example.com";  mobile="9303132333"; policyType="TERM";   premiumAmount=22000; startDate=$start; endDate=DateStr(365) }
)

Write-Host ""
Write-Host "Loading 20 demo policies relative to today: $($today.ToString('yyyy-MM-dd'))"
Write-Host "---------------------------------------------------"

$success = 0
$failed  = 0
foreach ($p in $policies) {
    $body = $p | ConvertTo-Json -Compress
    try {
        $r = Invoke-RestMethod -Uri $base -Method POST -Headers $h -Body $body
        Write-Host "OK  $($r.policyNumber) | $($r.policyType) | endDate: $($p.endDate) | $($r.status)"
        $success++
    } catch {
        Write-Host "ERR $($p.policyNumber) | $($_.Exception.Message)"
        $failed++
    }
}

Write-Host ""
Write-Host "Done: $success created, $failed failed."
Write-Host ""
Write-Host "Tier breakdown loaded:"
Write-Host "  30_DAY  : POL-001 to POL-004 (end in 22-28 days)"
Write-Host "  15_DAY  : POL-005 to POL-008 (end in  8-13 days)"
Write-Host "  7_DAY   : POL-009 to POL-011 (end in  2-5  days)"
Write-Host "  OVERDUE : POL-012 to POL-014 (end  3-8  days AGO)"
Write-Host "  RENEW   : POL-015, POL-016   (end in 4-6  days — will be renewed)"
Write-Host "  LAPSE   : POL-017, POL-018   (end 22-27 days AGO — will lapse)"
Write-Host "  ACTIVE  : POL-019, POL-020   (end 200-365 days away — stays ACTIVE)"
```

Run it:
```powershell
powershell -ExecutionPolicy Bypass -File load-demo-data.ps1
```

**Expected output:**
```
Loading 20 demo policies relative to today: 2026-09-15
---------------------------------------------------
OK  POL-001 | MOTOR  | endDate: 2026-10-11 | ACTIVE
OK  POL-002 | HEALTH | endDate: 2026-10-13 | ACTIVE
...
OK  POL-020 | TERM   | endDate: 2027-09-15 | ACTIVE
Done: 20 created, 0 failed.

Tier breakdown loaded:
  30_DAY  : POL-001 to POL-004 (end in 22-28 days)
  15_DAY  : POL-005 to POL-008 (end in  8-13 days)
  7_DAY   : POL-009 to POL-011 (end in  2-5  days)
  OVERDUE : POL-012 to POL-014 (end  3-8  days AGO)
  RENEW   : POL-015, POL-016   (end in 4-6  days — will be renewed)
  LAPSE   : POL-017, POL-018   (end 22-27 days AGO — will lapse)
  ACTIVE  : POL-019, POL-020   (end 200-365 days away — stays ACTIVE)
```

### Option B — Postman Pre-request Script

If you prefer Postman, create a **Collection Variable** called `TODAY`
and set it using a Pre-request Script on the collection:

```javascript
// Postman Collection Pre-request Script
// Add this to the collection level, not individual requests

const today = new Date();
const fmt = (d) => d.toISOString().split('T')[0];
const add = (days) => { const d = new Date(today); d.setDate(d.getDate() + days); return fmt(d); };

pm.collectionVariables.set("TODAY",    fmt(today));
pm.collectionVariables.set("START",    add(-365));
pm.collectionVariables.set("D_26",     add(26));
pm.collectionVariables.set("D_28",     add(28));
pm.collectionVariables.set("D_22",     add(22));
pm.collectionVariables.set("D_24",     add(24));
pm.collectionVariables.set("D_13",     add(13));
pm.collectionVariables.set("D_11",     add(11));
pm.collectionVariables.set("D_9",      add(9));
pm.collectionVariables.set("D_8",      add(8));
pm.collectionVariables.set("D_5",      add(5));
pm.collectionVariables.set("D_3",      add(3));
pm.collectionVariables.set("D_2",      add(2));
pm.collectionVariables.set("D_M3",     add(-3));
pm.collectionVariables.set("D_M6",     add(-6));
pm.collectionVariables.set("D_M8",     add(-8));
pm.collectionVariables.set("D_6",      add(6));
pm.collectionVariables.set("D_4",      add(4));
pm.collectionVariables.set("D_M22",    add(-22));
pm.collectionVariables.set("D_M27",    add(-27));
pm.collectionVariables.set("D_200",    add(200));
pm.collectionVariables.set("D_365",    add(365));
```

Then in each request body, use `{{D_26}}` instead of a hardcoded date.
Example for POL-001:
```json
{
  "policyNumber":  "POL-001",
  "holderName":    "Ramesh Kumar",
  "email":         "ramesh@example.com",
  "mobile":        "9876543210",
  "policyType":    "MOTOR",
  "premiumAmount": 5000,
  "startDate":     "{{START}}",
  "endDate":       "{{D_26}}"
}
```

### Verify all 20 loaded
```sql
SELECT policy_number, holder_name, policy_type, end_date, status
FROM policies ORDER BY end_date;
-- Expected: 20 rows, all status = ACTIVE
```

---

## Part 3 — Complete Demo Script

### Demo Step 1 — Confirm data loaded and tiers are correct

```
GET http://localhost:8080/demo/today
```
Note today's date. Then verify in H2:

```sql
SELECT policy_number,
       end_date,
       DATEDIFF('DAY', CURRENT_DATE, end_date) as days_left
FROM policies
ORDER BY days_left DESC;
```

You'll see the exact days-left for each policy, confirming every tier grouping.

---

### Demo Step 2 — Idempotency (30 seconds)

Send POL-001 again (exact same body):
```
POST http://localhost:8080/policies
{ same POL-001 body }
```

**Show:**
- First call → `201 Created`, `"duplicate": false`
- Second call → `200 OK`, `"duplicate": true`
- H2: `SELECT COUNT(*) FROM policies WHERE policy_number = 'POL-001'` → 1 (not 2)

---

### Demo Step 3 — Run Job 1 (Detection)

```
POST http://localhost:8080/jobs/detection/run
Expected: "Detection job triggered"
```

**Expected console:**
```
Detection job: scanning 18 policies (ACTIVE + RENEWAL_DUE) with endDate <= <today+30>
Job1 (Detection) summary:
  created[30_DAY]=4
  created[15_DAY]=4
  created[7_DAY]=5
  created[OVERDUE]=5
  skipped=2
```

`skipped=2` = POL-019 and POL-020 (200 and 365 days away — not in the 30-day window).

**Show in H2:**
```sql
-- Policy statuses
SELECT policy_number, status, end_date FROM policies ORDER BY end_date;

-- 18 outbox rows, correct tiers and channels
SELECT policy_number, tier, channel, status, reminder_date
FROM reminder_outbox ORDER BY tier, policy_number;
```

**Point out:**
- POL-004, POL-008, POL-014 have `channel = SMS` (no email provided)
- All others show `channel = EMAIL`
- POL-019, POL-020 still `status = ACTIVE` — correctly skipped

---

### Demo Step 4 — No Duplicates on Rerun

```
POST http://localhost:8080/jobs/detection/run
```

**Expected console:** `skipped=18`

**Show in H2:**
```sql
SELECT COUNT(*) FROM reminder_outbox;
-- Expected: 18 (not 36)
```

**Tell mentor:** "The `UNIQUE(policy_number, tier, reminder_date)` constraint on the
database rejected all 18 duplicate inserts — enforced at the database level,
not just application code. This is the transactional outbox pattern working correctly."

---

### Demo Step 5 — Notification Poller (Automatic)

Do nothing — wait 10–15 seconds. Watch the console:

```
Worker picked up 5 reminder(s)
Reminder 1 sent (policy POL-012, tier OVERDUE)
Reminder 2 — invalid contact for policy POL-014. Flagged for manual follow-up.
Reminder 3 timed out (attempt 1), next retry at <timestamp>
Reminder 4 sent (policy POL-013, tier OVERDUE)
Reminder 5 timed out (attempt 1), next retry at <timestamp>
```

**Show in H2 — all three outcomes:**
```sql
SELECT n.policy_number, n.tier, n.channel,
       n.attempt_number, n.outcome, n.attempted_at
FROM notification_log n ORDER BY n.attempted_at DESC;

-- Manual follow-up policies
SELECT policy_number, needs_manual_follow_up
FROM policies WHERE needs_manual_follow_up = TRUE;

-- Outbox state after processing
SELECT policy_number, tier, status, retry_count
FROM reminder_outbox ORDER BY id;
```

Wait a few more minutes — timed-out reminders retry automatically with exponential
backoff (1 min → 4 min → 16 min). Watch `retry_count` increment in H2.

After 3 timeouts, the reminder moves to DLQ:
```
Reminder X moved to DLQ (policy POL-XXX, tier XXX): Gateway timeout after 3 attempts
```

```sql
SELECT * FROM reminder_dlq;
```

---

### Demo Step 6 — DLQ Requeue

```
GET http://localhost:8080/dlq
```
Note an entry's `id` (e.g. `1`):
```
POST http://localhost:8080/dlq/1/requeue
```

**Show in H2:**
```sql
SELECT * FROM reminder_dlq;        -- entry gone
SELECT * FROM reminder_outbox WHERE status = 'PENDING' ORDER BY id DESC LIMIT 5;
-- New row appeared with retryCount=0
```

---

### Demo Step 7 — Renewal Cancels Pending Reminders

```
PUT http://localhost:8080/policies/POL-015/renew
{
  "paymentReference": "PAY-DEMO-015",
  "newEndDate":       "<today + 366 days>"
}
```

```
PUT http://localhost:8080/policies/POL-016/renew
{
  "paymentReference": "PAY-DEMO-016",
  "newEndDate":       "<today + 366 days>"
}
```

> For `newEndDate` use any date 1 year from today. In Postman use `{{D_365}}` if
> you set up the pre-request script, or just type a date manually.

**Expected console:**
```
Policy POL-015 renewed: RENEWAL_DUE -> ACTIVE, paymentRef=PAY-DEMO-015
Cancelled 1 pending reminder(s) for policy POL-015
Policy POL-016 renewed: RENEWAL_DUE -> ACTIVE, paymentRef=PAY-DEMO-016
Cancelled 1 pending reminder(s) for policy POL-016
```

**Show in H2:**
```sql
SELECT policy_number, status, last_renewed_at, end_date
FROM policies WHERE policy_number IN ('POL-015','POL-016');
-- status=ACTIVE, last_renewed_at=today

SELECT policy_number, tier, status
FROM reminder_outbox WHERE policy_number IN ('POL-015','POL-016');
-- status=CANCELLED
```

**Get full policy history:**
```
GET http://localhost:8080/policies/POL-015
```
Shows `status: ACTIVE` and `reminderHistory` with attempt log.

---

### Demo Step 8 — Multiple Tiers Fire as Clock Advances

This proves the critical fix: a policy already `RENEWAL_DUE` (with its `30_DAY`
reminder sent) will still get its `15_DAY`, `7_DAY`, and `OVERDUE` reminders
as time passes — because the reader scans both `ACTIVE` and `RENEWAL_DUE` policies.

**Advance clock to put POL-001 in the 15-day window:**
POL-001 ends in 26 days from today. Advance by 12 days to make it 14 days away:

```
POST http://localhost:8080/demo/advance-clock?date=<today + 12 days>
GET  http://localhost:8080/demo/today
Expected: today + 12 days

POST http://localhost:8080/jobs/detection/run
```

**Expected console:** `created[15_DAY]=X` — includes POL-001 getting its second reminder.

**Show in H2:**
```sql
SELECT policy_number, tier, reminder_date
FROM reminder_outbox WHERE policy_number = 'POL-001' ORDER BY id;
-- Expected: 2 rows
--   30_DAY | <original today>
--   15_DAY | <today + 12 days>
```

**Advance again to 7-day window:**
```
POST http://localhost:8080/demo/advance-clock?date=<today + 20 days>
POST http://localhost:8080/jobs/detection/run
```

POL-001 now gets its `7_DAY` reminder. Three tiers, three different dates, one policy.

---

### Demo Step 9 — Lapse Job

POL-017 ended 22 days ago. Grace period = 15 days. Lapse cutoff = 7 days ago.
POL-018 ended 27 days ago. Already past lapse cutoff.
Both are eligible to lapse immediately with no clock change needed.

```
POST http://localhost:8080/jobs/lapse/run
```

**Expected console:**
```
Lapse job: found 2 RENEWAL_DUE policies with endDate <= <cutoff date>
Job2 (Lapse) summary: lapsed=2 skipped=X
```

**Show in H2:**
```sql
SELECT policy_number, status FROM policies
WHERE policy_number IN ('POL-017','POL-018');
-- Expected: LAPSED

SELECT policy_number, status FROM policies
WHERE policy_number = 'POL-001';
-- Expected: RENEWAL_DUE — still inside grace period, correctly not lapsed
```

---

### Demo Step 10 — Dynamic Scheduling (DB-Driven)

**View current schedules from the database:**
```
GET http://localhost:8080/schedules
```
Shows all 3 jobs with their cron expressions stored in `job_schedule` table.

**Change Detection to fire every 2 minutes (live demo mode):**
```
PUT http://localhost:8080/schedules/renewalDetectionJob/cron
{ "cronExpr": "0 */2 * * * *" }
```

**Expected console immediately — no restart:**
```
Rescheduled 'renewalDetectionJob' with new cron '0 */2 * * * *' (live, no restart needed)
```

Wait 2 minutes — job fires automatically without any Postman trigger:
```
Scheduled trigger fired: renewalDetectionJob
Detection job: scanning X policies...
```

**Disable the job:**
```
PUT http://localhost:8080/schedules/renewalDetectionJob/enabled
{ "enabled": false }
```

Wait 2+ minutes — nothing fires.

**Restore:**
```
PUT http://localhost:8080/schedules/renewalDetectionJob/enabled
{ "enabled": true }

PUT http://localhost:8080/schedules/renewalDetectionJob/cron
{ "cronExpr": "0 0 1 * * *" }
```

---

### Demo Step 11 — Funnel Report

```
POST http://localhost:8080/jobs/report/run
```

**Expected console:**
```
Renewal Funnel Report (<simulated date>): ACTIVE=4 RENEWAL_DUE=X RENEWED_TODAY=2 LAPSED=2
  Tier 30_DAY: sent=X failedInvalidContact=X deadLettered=X
  Tier 15_DAY: sent=X failedInvalidContact=X deadLettered=X
  Tier 7_DAY:  sent=X failedInvalidContact=X deadLettered=X
  Tier OVERDUE:sent=X failedInvalidContact=X deadLettered=X
Funnel report written to .../reports/funnel-report-<date>.csv
```

Open the CSV from `reports/` folder and show the mentor the file.

---

### Demo Step 12 — Restartability

**Show Spring Batch tracking tables:**
```sql
SELECT i.job_name, e.status, e.start_time, e.end_time
FROM BATCH_JOB_INSTANCE i
JOIN BATCH_JOB_EXECUTION e ON e.job_instance_id = i.job_instance_id
ORDER BY e.start_time DESC;
```

Shows every job run (COMPLETED and any FAILED ones). Spring Batch tracked every
chunk — if the app had crashed mid-job, it would resume from the last committed
chunk on restart, not process everything again from scratch.

---

### Demo Step 13 — Reset

```
POST http://localhost:8080/demo/reset-clock
GET  http://localhost:8080/demo/today
Expected: today's real date
```

---

## Part 4 — Policy Data Reference (Relative to Demo Day)

| # | Policy | Holder | Type | End Date (offset) | Initial Tier | Demo Purpose |
|---|---|---|---|---|---|---|
| 1 | POL-001 | Ramesh Kumar | MOTOR | today+26 | 30_DAY | Multi-tier progression |
| 2 | POL-002 | Aasha Patel | HEALTH | today+28 | 30_DAY | |
| 3 | POL-003 | Vijay Nair | TERM | today+22 | 30_DAY | |
| 4 | POL-004 | Meena Krishnan | MOTOR | today+24 | 30_DAY | **No email → SMS channel** |
| 5 | POL-005 | Karthik Raja | HEALTH | today+13 | 15_DAY | |
| 6 | POL-006 | Saranya Devi | TERM | today+11 | 15_DAY | |
| 7 | POL-007 | Balu Sundaram | MOTOR | today+9 | 15_DAY | |
| 8 | POL-008 | Deepa Mohan | HEALTH | today+8 | 15_DAY | **No email → SMS channel** |
| 9 | POL-009 | Priya Menon | TERM | today+5 | 7_DAY | |
| 10 | POL-010 | Anbu Selvam | MOTOR | today+3 | 7_DAY | |
| 11 | POL-011 | Kavitha Rajan | HEALTH | today+2 | 7_DAY | |
| 12 | POL-012 | Suresh Babu | MOTOR | today−3 | OVERDUE | |
| 13 | POL-013 | Geetha Lakshmi | HEALTH | today−6 | OVERDUE | |
| 14 | POL-014 | Murugan Raj | TERM | today−8 | OVERDUE | **No email → SMS channel** |
| 15 | POL-015 | Lakshmi Priya | HEALTH | today+6 | 7_DAY | **Will be RENEWED** |
| 16 | POL-016 | Dinesh Barath | MOTOR | today+4 | 7_DAY | **Will be RENEWED** |
| 17 | POL-017 | Selvakumar T | TERM | today−22 | OVERDUE | **Will LAPSE** (past grace) |
| 18 | POL-018 | Revathi M | HEALTH | today−27 | OVERDUE | **Will LAPSE** (past grace) |
| 19 | POL-019 | Mani Iyer | MOTOR | today+200 | none | **Stays ACTIVE — skipped** |
| 20 | POL-020 | Santhiya R | TERM | today+365 | none | **Stays ACTIVE — skipped** |

---

## Part 5 — Expected Job 1 Output

After first `POST /jobs/detection/run` with all 20 policies:

```
Detection job: scanning 18 policies (ACTIVE + RENEWAL_DUE) with endDate <= <today+30>
Job1 (Detection) summary:
  created[30_DAY]=4   → POL-001, POL-002, POL-003, POL-004
  created[15_DAY]=4   → POL-005, POL-006, POL-007, POL-008
  created[7_DAY]=5    → POL-009, POL-010, POL-011, POL-015, POL-016
  created[OVERDUE]=5  → POL-012, POL-013, POL-014, POL-017, POL-018
  skipped=2           → POL-019, POL-020 (endDate > 30 days away)
```

After second immediate run:
```
Job1 (Detection) summary: skipped=18
```

---

## Part 6 — H2 Quick Queries During Demo

```sql
-- Full policy snapshot with days remaining
SELECT policy_number, holder_name, status,
       end_date,
       DATEDIFF('DAY', CURRENT_DATE, end_date) AS days_left,
       needs_manual_follow_up,
       last_renewed_at
FROM policies ORDER BY days_left;

-- Outbox state — all reminders
SELECT policy_number, tier, channel, status, retry_count,
       next_retry_at, reminder_date
FROM reminder_outbox ORDER BY tier, policy_number;

-- Tier distribution summary
SELECT tier,
       COUNT(*)                                                    AS total,
       SUM(CASE WHEN status='SENT'      THEN 1 ELSE 0 END)        AS sent,
       SUM(CASE WHEN status='FAILED'    THEN 1 ELSE 0 END)        AS failed,
       SUM(CASE WHEN status='PENDING'   THEN 1 ELSE 0 END)        AS pending,
       SUM(CASE WHEN status='CANCELLED' THEN 1 ELSE 0 END)        AS cancelled
FROM reminder_outbox GROUP BY tier ORDER BY tier;

-- Full notification attempt history
SELECT n.policy_number, n.tier, n.channel,
       n.attempt_number, n.outcome, n.error_message, n.attempted_at
FROM notification_log n ORDER BY n.attempted_at DESC;

-- Who needs manual follow-up
SELECT policy_number, holder_name, mobile
FROM policies WHERE needs_manual_follow_up = TRUE;

-- DLQ contents
SELECT id, policy_number, tier, retry_count, last_error, moved_at
FROM reminder_dlq ORDER BY moved_at DESC;

-- Job schedules from DB
SELECT job_name, cron_expr, enabled, updated_at FROM job_schedule;

-- Spring Batch execution history
SELECT i.job_name, e.status, e.start_time, e.end_time
FROM BATCH_JOB_INSTANCE i
JOIN BATCH_JOB_EXECUTION e ON e.job_instance_id = i.job_instance_id
ORDER BY e.start_time DESC;
```

---

## Part 7 — Quick API Reference

| Method | URL | Body / Params |
|---|---|---|
| POST | `/policies` | `{ policyNumber, holderName, email?, mobile?, policyType, premiumAmount, startDate, endDate }` |
| PUT | `/policies/{policyNumber}/renew` | `{ paymentReference, newEndDate }` |
| GET | `/policies/{policyNumber}` | — |
| POST | `/jobs/detection/run` | — |
| POST | `/jobs/lapse/run` | — |
| POST | `/jobs/report/run` | — |
| GET | `/schedules` | — |
| PUT | `/schedules/{jobName}/cron` | `{ "cronExpr": "0 0 1 * * *" }` |
| PUT | `/schedules/{jobName}/enabled` | `{ "enabled": true }` |
| GET | `/dlq` | — |
| POST | `/dlq/{id}/requeue` | — |
| GET | `/demo/today` | — |
| POST | `/demo/advance-clock` | `?date=YYYY-MM-DD` |
| POST | `/demo/reset-clock` | — |

**Valid `{jobName}` values:**
- `renewalDetectionJob`
- `policyLapseJob`
- `renewalFunnelReportJob`
