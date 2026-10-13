package edu.university.badminton.dto;

import edu.university.badminton.model.Court;

/**
 * Public response representation of a court.
 */
public record CourtResponseDto(
        Long id,
        Integer courtNumber,
        Boolean active
) {
    public static CourtResponseDto fromEntity(Court court) {
        return new CourtResponseDto(
                court.getId(),
                court.getCourtNumber(),
                court.getActive()
        );
    }
}
