package edu.university.badminton.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Development implementation of EmailService.
 * <p>
 * Safely logs verification tokens and URLs to application logs for testing.
 * Explicitly notes that SMTP delivery was NOT performed, preventing false assumptions of delivery.
 */
@Service
@ConditionalOnProperty(name = "app.email.provider", havingValue = "dev", matchIfMissing = true)
public class DevConsoleEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(DevConsoleEmailService.class);

    @Value("${app.email.verification-base-url:http://localhost:8081}")
    private String verificationBaseUrl = "http://localhost:8081";

    @Override
    public String sendVerificationEmail(String recipientEmail, String studentName, String rawToken) {
        String verificationUrl = verificationBaseUrl + "/api/auth/verify-email?token=" + rawToken;
        log.info("[DEV EMAIL DISPATCH] (Testing Mode: Email logged to console, NOT delivered to external mailbox). Recipient: '{}' <{}>. Token: {}. Link: {}",
                studentName, recipientEmail, rawToken, verificationUrl);
        return "DEV_MODE_LOGGED: Email logged to console. Real SMTP delivery was not performed.";
    }

    public void setVerificationBaseUrl(String verificationBaseUrl) {
        this.verificationBaseUrl = verificationBaseUrl;
    }
}
