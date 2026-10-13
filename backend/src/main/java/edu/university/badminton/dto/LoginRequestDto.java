package edu.university.badminton.dto;

/**
 * Request payload for user login (accepts university email or admission number).
 */
public record LoginRequestDto(
        String identifier,
        String password
) {
}
