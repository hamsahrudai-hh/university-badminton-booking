package edu.university.badminton.config;

import edu.university.badminton.model.Role;

import java.security.Principal;

/**
 * Authenticated user principal representing a verified session identity.
 */
public record UserPrincipal(
        Long id,
        String email,
        String admissionNumber,
        String fullName,
        Role role
) implements Principal {

    @Override
    public String getName() {
        return fullName != null ? fullName : email;
    }
}
