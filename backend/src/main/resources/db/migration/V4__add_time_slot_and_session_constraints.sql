-- Migration V4: Add validation constraints on session duration and slot time windows
-- Database: badminton_booking

ALTER TABLE session_configs
    ADD CONSTRAINT chk_session_duration CHECK (slot_duration_minutes > 0),
    ADD CONSTRAINT chk_session_time_window CHECK (end_time > start_time);

ALTER TABLE time_slots
    ADD CONSTRAINT chk_slot_time_window CHECK (end_time > start_time);
