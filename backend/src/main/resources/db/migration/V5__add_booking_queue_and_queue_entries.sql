-- Migration V5: Add student booking queue and queue entries tables
-- Database: badminton_booking

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
