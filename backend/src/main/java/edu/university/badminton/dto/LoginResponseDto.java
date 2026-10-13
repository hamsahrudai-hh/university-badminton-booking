package edu.university.badminton.dto;

import edu.university.badminton.model.Role;

/**
 * Public response returned on successful authentication containing the signed JWT.
 */
public record LoginResponseDto(
        String token,
        String tokenType,
        long expiresInSeconds,
        Long id,
        String fullName,
        String email,
        String admissionNumber,
        Role role,
        Integer weeklyCredits,
        Double reliabilityRating
) {
}
