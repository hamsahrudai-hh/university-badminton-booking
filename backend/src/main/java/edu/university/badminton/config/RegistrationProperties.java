package edu.university.badminton.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Configuration properties for student registration and university domain verification.
 */
@Component
public class RegistrationProperties {

    @Value("${app.registration.allowed-email-domain:vitapstudent.ac.in}")
    private String allowedEmailDomain = "vitapstudent.ac.in";

    @Value("${app.registration.verification-token-expiry-hours:24}")
    private int verificationTokenExpiryHours = 24;

    public RegistrationProperties() {
    }

    public RegistrationProperties(String allowedEmailDomain, int verificationTokenExpiryHours) {
        this.allowedEmailDomain = allowedEmailDomain;
        this.verificationTokenExpiryHours = verificationTokenExpiryHours;
    }

    /**
     * Checks if the given email has a valid university domain matching the configured allowed domain.
     * Whitespace is trimmed and case is compared insensitively.
     *
     * @param email candidate student email
     * @return true if valid and ends with @allowedEmailDomain, false otherwise
     */
    public boolean isDomainAllowed(String email) {
        if (email == null) {
            return false;
        }
        String trimmed = email.trim().toLowerCase(Locale.ROOT);
        String expectedSuffix = "@" + (allowedEmailDomain != null ? allowedEmailDomain.trim().toLowerCase(Locale.ROOT) : "");
        return expectedSuffix.length() > 1 && trimmed.endsWith(expectedSuffix) && trimmed.length() > expectedSuffix.length();
    }

    public String getAllowedEmailDomain() {
        return allowedEmailDomain;
    }

    public void setAllowedEmailDomain(String allowedEmailDomain) {
        this.allowedEmailDomain = allowedEmailDomain;
    }

    public int getVerificationTokenExpiryHours() {
        return verificationTokenExpiryHours;
    }

    public void setVerificationTokenExpiryHours(int verificationTokenExpiryHours) {
        this.verificationTokenExpiryHours = verificationTokenExpiryHours;
    }
}
