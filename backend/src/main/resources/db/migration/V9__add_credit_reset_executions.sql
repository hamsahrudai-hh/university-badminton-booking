-- V9: Add table to track automated weekly student credit reset executions
-- Ensures deduplication across restarts and prevents multiple resets within the same weekly period.

CREATE TABLE IF NOT EXISTS credit_reset_executions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    period_key VARCHAR(30) NOT NULL,
    executed_at DATETIME(6) NOT NULL,
    students_reset_count INT NOT NULL DEFAULT 0,
    triggered_by VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    notes VARCHAR(255) NULL,
    CONSTRAINT uk_credit_reset_period_key UNIQUE (period_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
