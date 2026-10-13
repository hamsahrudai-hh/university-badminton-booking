-- Migration V6: Add bookings and booking players tables
-- Database: badminton_booking

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
