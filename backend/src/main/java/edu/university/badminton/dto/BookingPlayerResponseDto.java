package edu.university.badminton.dto;

import edu.university.badminton.model.BookingPlayer;

/**
 * Public response representation of a player in a confirmed booking.
 */
public record BookingPlayerResponseDto(
        Long studentId,
        String studentName,
        String admissionNumber,
        Integer spotNumber,
        String qrToken,
        boolean checkedIn,
        int creditsDeducted,
        int remainingWeeklyCredits
) {
    public static BookingPlayerResponseDto fromEntity(BookingPlayer player) {
        return new BookingPlayerResponseDto(
                player.getStudent().getId(),
                player.getStudent().getFullName(),
                player.getStudent().getAdmissionNumber(),
                player.getSpotNumber(),
                player.getQrToken(),
                Boolean.TRUE.equals(player.getCheckedIn()),
                player.getCreditsDeducted(),
                player.getStudent().getWeeklyCredits()
        );
    }
}
