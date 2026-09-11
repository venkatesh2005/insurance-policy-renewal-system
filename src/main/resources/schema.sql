-- ============================================
-- POLICIES
-- ============================================

CREATE TABLE IF NOT EXISTS policies (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    policy_number          VARCHAR(50)     NOT NULL,
    holder_name            VARCHAR(200)    NOT NULL,
    email                  VARCHAR(200),
    mobile                 VARCHAR(20),
    policy_type            VARCHAR(20)     NOT NULL,
    premium_amount         DECIMAL(12, 2)  NOT NULL,
    start_date             DATE            NOT NULL,
    end_date               DATE            NOT NULL,
    status                 VARCHAR(20)     NOT NULL,
    needs_manual_follow_up BOOLEAN         NOT NULL DEFAULT FALSE,
    version                BIGINT          NOT NULL DEFAULT 0,
    last_renewed_at        DATE,
    created_at             TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_policy_number
    UNIQUE (policy_number),

    CONSTRAINT chk_premium_positive
    CHECK (premium_amount > 0),

    CONSTRAINT chk_end_after_start
    CHECK (end_date > start_date),

    CONSTRAINT chk_policy_type
    CHECK (policy_type IN ('HEALTH', 'MOTOR', 'TERM')),

    CONSTRAINT chk_policy_status
    CHECK (status IN ('ACTIVE', 'RENEWAL_DUE', 'LAPSED'))
    );

CREATE INDEX IF NOT EXISTS idx_policies_status_end_date
    ON policies (status, end_date);


-- ============================================
-- REMINDER_OUTBOX
-- ============================================

CREATE TABLE IF NOT EXISTS reminder_outbox (
   id                BIGINT AUTO_INCREMENT PRIMARY KEY,
   policy_number     VARCHAR(50)     NOT NULL,
    tier              VARCHAR(20)     NOT NULL,
    channel           VARCHAR(20)     NOT NULL,
    payload           VARCHAR(1000)   NOT NULL,
    status            VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    retry_count       INT             NOT NULL DEFAULT 0,
    next_retry_at     TIMESTAMP       NOT NULL,
    reminder_date     DATE            NOT NULL,
    created_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_outbox_policy
    FOREIGN KEY (policy_number)
    REFERENCES policies (policy_number),

    CONSTRAINT uq_policy_tier_day
    UNIQUE (policy_number, tier, reminder_date),

    CONSTRAINT chk_reminder_tier
    CHECK (tier IN ('30_DAY', '15_DAY', '7_DAY', 'OVERDUE')),

    CONSTRAINT chk_reminder_channel
    CHECK (channel IN ('EMAIL', 'SMS')),

    CONSTRAINT chk_reminder_status
    CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'CANCELLED'))
    );

CREATE INDEX IF NOT EXISTS idx_outbox_poll
    ON reminder_outbox (status, next_retry_at);

CREATE INDEX IF NOT EXISTS idx_outbox_policy_number
    ON reminder_outbox (policy_number);


-- ============================================
-- NOTIFICATION_LOG
-- ============================================

CREATE TABLE IF NOT EXISTS notification_log (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    outbox_id         BIGINT          NOT NULL,
    policy_number     VARCHAR(50)     NOT NULL,
    tier              VARCHAR(20)     NOT NULL,
    channel           VARCHAR(20)     NOT NULL,
    attempt_number    INT             NOT NULL,
    outcome           VARCHAR(20)     NOT NULL,
    error_message     VARCHAR(500),
    attempted_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_log_outbox
    FOREIGN KEY (outbox_id)
    REFERENCES reminder_outbox (id),

    CONSTRAINT chk_log_outcome
    CHECK (outcome IN ('SUCCESS', 'INVALID_CONTACT', 'GATEWAY_TIMEOUT'))
    );

CREATE INDEX IF NOT EXISTS idx_log_policy_number
    ON notification_log (policy_number);

CREATE INDEX IF NOT EXISTS idx_log_outbox_id
    ON notification_log (outbox_id);


-- ============================================
-- REMINDER_DLQ
-- ============================================

CREATE TABLE IF NOT EXISTS reminder_dlq (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    outbox_id         BIGINT          NOT NULL,
    policy_number     VARCHAR(50)     NOT NULL,
    tier              VARCHAR(20)     NOT NULL,
    channel           VARCHAR(20)     NOT NULL,
    payload           VARCHAR(1000)   NOT NULL,
    retry_count       INT             NOT NULL,
    last_error        VARCHAR(500)    NOT NULL,
    moved_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_dlq_outbox
    FOREIGN KEY (outbox_id)
    REFERENCES reminder_outbox (id)
    );

CREATE INDEX IF NOT EXISTS idx_dlq_policy_number
    ON reminder_dlq (policy_number);

-- ============================================
-- JOB_SCHEDULE
-- ============================================

CREATE TABLE IF NOT EXISTS job_schedule (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_name    VARCHAR(100) NOT NULL,
    cron_expr   VARCHAR(100) NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    description VARCHAR(300),
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_job_name UNIQUE (job_name)
);

-- Seed default schedules on first startup.
-- MERGE INTO leaves existing rows untouched — so any cron changes
-- made via the API are preserved across restarts.
MERGE INTO job_schedule (job_name, cron_expr, enabled, description)
    KEY (job_name)
    VALUES
    ('renewalDetectionJob',    '0 0 1 * * *', TRUE, 'Daily detection of expiring policies — runs at 1am'),
    ('policyLapseJob',         '0 0 2 * * *', TRUE, 'Daily lapse of overdue policies — runs at 2am'),
    ('renewalFunnelReportJob', '0 0 3 * * *', TRUE, 'Daily funnel report generation — runs at 3am');
