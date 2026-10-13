-- Migration V7: Add student authentication and email verification
-- Database: badminton_booking

ALTER TABLE students
    ADD COLUMN password_hash VARCHAR(255) NULL AFTER admission_number,
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE AFTER reliability_rating,
    ADD COLUMN email_verified_at DATETIME(6) NULL AFTER email_verified;

CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_evt_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_evt_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
    INDEX idx_evt_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
