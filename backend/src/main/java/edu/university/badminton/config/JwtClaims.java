package edu.university.badminton.config;

import edu.university.badminton.model.Role;

/**
 * Validated claims extracted from a cryptographically verified JWT token.
 */
public record JwtClaims(
        Long studentId,
        String email,
        String admissionNumber,
        String fullName,
        Role role
) {
}
