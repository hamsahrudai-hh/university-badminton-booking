package edu.university.badminton.service;

/**
 * Service contract for dispatching verification emails.
 */
public interface EmailService {

    /**
     * Dispatches an email containing the student's verification link and token.
     *
     * @param recipientEmail the normalized university email
     * @param studentName the recipient's full name
     * @param rawToken the raw unhashed verification token
     * @return dispatch status description
     */
    String sendVerificationEmail(String recipientEmail, String studentName, String rawToken);
}
