-- Migration V2: Add schedule and time-slot persistence models
-- Database: badminton_booking

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
    CONSTRAINT uk_session_type UNIQUE (session_type)
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
    CONSTRAINT fk_slot_court FOREIGN KEY (court_id) REFERENCES courts (id),
    CONSTRAINT fk_slot_session_config FOREIGN KEY (session_config_id) REFERENCES session_configs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
