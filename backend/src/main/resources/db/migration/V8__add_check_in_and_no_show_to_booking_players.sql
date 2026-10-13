-- Migration V8: Add staff check-in tracking and no-show processing to booking players
-- Database: badminton_booking

ALTER TABLE booking_players
    ADD COLUMN checked_in_by VARCHAR(100) NULL AFTER checked_in_at,
    ADD COLUMN no_show BOOLEAN NOT NULL DEFAULT FALSE AFTER checked_in_by,
    ADD COLUMN no_show_processed_at DATETIME(6) NULL AFTER no_show;
