package edu.university.badminton.dto;

/**
 * Request representation of a player assigned to a spot within a group booking.
 */
public record BookingPlayerAssignmentDto(
        Long studentId,
        Integer spotNumber
) {
}
