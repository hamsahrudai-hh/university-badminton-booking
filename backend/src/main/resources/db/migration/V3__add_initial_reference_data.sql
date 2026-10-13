-- Migration V3: Insert initial reference data (Courts 1-4 and Session Configurations)
-- Database: badminton_booking

-- 1. Insert four badminton courts (Courts 1 through 4)
-- Idempotent via uk_court_number UNIQUE constraint
INSERT INTO courts (court_number, active, created_at)
VALUES
    (1, TRUE, NOW(6)),
    (2, TRUE, NOW(6)),
    (3, TRUE, NOW(6)),
    (4, TRUE, NOW(6))
ON DUPLICATE KEY UPDATE
    active = VALUES(active);

-- 2. Insert initial session configurations
-- Idempotent via uk_session_type UNIQUE constraint
-- Morning: 06:00 to 08:30, 30-min slots, queue opens 21:00 previous day, booking starts 21:05 previous day
-- Evening: 15:30 to 19:00, 30-min slots, queue opening is configurable (placeholder: 12:00 same day, booking starts 12:05)
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
