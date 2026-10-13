-- Schema definition for University Badminton Court Booking System
-- Database: badminton_booking

CREATE TABLE IF NOT EXISTS students (
    id BIGINT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(255) NOT NULL,
    university_email VARCHAR(255) NOT NULL,
    admission_number VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NULL,
    role VARCHAR(50) NOT NULL,
    weekly_credits INT NOT NULL DEFAULT 6,
    reliability_rating DOUBLE NOT NULL DEFAULT 100.0,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    email_verified_at DATETIME(6) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_student_email UNIQUE (university_email),
    CONSTRAINT uk_student_admission UNIQUE (admission_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS courts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    court_number INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_court_number UNIQUE (court_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS session_configs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_type VARCHAR(50) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    slot_duration_minutes INT NOT NULL DEFAULT 30,
    queue_open_time TIME NOT NULL,
    booking_start_time TIME NOT NULL,
    queue_days_in_advance INT NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_session_type UNIQUE (session_type),
    CONSTRAINT chk_session_duration CHECK (slot_duration_minutes > 0),
    CONSTRAINT chk_session_time_window CHECK (end_time > start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS time_slots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    court_id BIGINT NOT NULL,
    session_config_id BIGINT NOT NULL,
    slot_date DATE NOT NULL,
    start_time DATETIME(6) NOT NULL,
    end_time DATETIME(6) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE',
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_court_slot_time UNIQUE (court_id, start_time),
    CONSTRAINT chk_slot_time_window CHECK (end_time > start_time),
    CONSTRAINT fk_slot_court FOREIGN KEY (court_id) REFERENCES courts (id),
    CONSTRAINT fk_slot_session_config FOREIGN KEY (session_config_id) REFERENCES session_configs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS booking_queues (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_config_id BIGINT NOT NULL,
    session_type VARCHAR(50) NOT NULL,
    session_date DATE NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_queue_session_date UNIQUE (session_date, session_type),
    CONSTRAINT fk_queue_session_config FOREIGN KEY (session_config_id) REFERENCES session_configs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS queue_entries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    queue_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'WAITING',
    joined_at DATETIME(6) NOT NULL,
    admitted_at DATETIME(6) NULL,
    expires_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_queue_student UNIQUE (queue_id, student_id),
    CONSTRAINT fk_queue_entry_queue FOREIGN KEY (queue_id) REFERENCES booking_queues (id),
    CONSTRAINT fk_queue_entry_student FOREIGN KEY (student_id) REFERENCES students (id),
    INDEX idx_queue_entry_fifo (queue_id, status, joined_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS bookings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    time_slot_id BIGINT NOT NULL,
    booking_user_id BIGINT NOT NULL,
    queue_entry_id BIGINT NULL,
    idempotency_key VARCHAR(100) NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'CONFIRMED',
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_booking_time_slot UNIQUE (time_slot_id),
    CONSTRAINT uk_booking_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_booking_time_slot FOREIGN KEY (time_slot_id) REFERENCES time_slots (id),
    CONSTRAINT fk_booking_user FOREIGN KEY (booking_user_id) REFERENCES students (id),
    CONSTRAINT fk_booking_queue_entry FOREIGN KEY (queue_entry_id) REFERENCES queue_entries (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS booking_players (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    spot_number INT NOT NULL,
    qr_token VARCHAR(255) NOT NULL,
    checked_in BOOLEAN NOT NULL DEFAULT FALSE,
    checked_in_at DATETIME(6) NULL,
    checked_in_by VARCHAR(100) NULL,
    no_show BOOLEAN NOT NULL DEFAULT FALSE,
    no_show_processed_at DATETIME(6) NULL,
    credits_deducted INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_booking_player_spot UNIQUE (booking_id, spot_number),
    CONSTRAINT uk_booking_player_student UNIQUE (booking_id, student_id),
    CONSTRAINT uk_booking_player_qr_token UNIQUE (qr_token),
    CONSTRAINT fk_booking_player_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_booking_player_student FOREIGN KEY (student_id) REFERENCES students (id),
    INDEX idx_booking_player_lookup (student_id, booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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

-- ========================================================
-- Initial Reference Data (Idempotent seed data)
-- ========================================================

-- Courts 1 through 4
INSERT INTO courts (court_number, active, created_at)
VALUES
    (1, TRUE, NOW(6)),
    (2, TRUE, NOW(6)),
    (3, TRUE, NOW(6)),
    (4, TRUE, NOW(6))
ON DUPLICATE KEY UPDATE active = VALUES(active);

-- Initial recurring session configurations
-- Morning: 06:00 to 08:30, 30m slots, queue opens 21:00 previous day, booking starts 21:05 previous day
-- Evening: 15:30 to 19:00, 30m slots, queue opening is configurable (placeholder: 12:00 same day, booking starts 12:05)
INSERT INTO session_configs (
    session_type,
    start_time,
    end_time,
    slot_duration_minutes,
    queue_open_time,
    booking_start_time,
    queue_days_in_advance,
    active,
    created_at
)
VALUES
    ('MORNING', '06:00:00', '08:30:00', 30, '21:00:00', '21:05:00', 1, TRUE, NOW(6)),
    ('EVENING', '15:30:00', '19:00:00', 30, '12:00:00', '12:05:00', 0, TRUE, NOW(6))
ON DUPLICATE KEY UPDATE
    start_time = VALUES(start_time),
    end_time = VALUES(end_time),
    slot_duration_minutes = VALUES(slot_duration_minutes),
    active = VALUES(active);

