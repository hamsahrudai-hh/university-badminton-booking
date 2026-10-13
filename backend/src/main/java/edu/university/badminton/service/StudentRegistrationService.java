package edu.university.badminton.service;

import edu.university.badminton.config.RegistrationProperties;
import edu.university.badminton.dto.RegisterStudentRequestDto;
import edu.university.badminton.dto.RegistrationResponseDto;
import edu.university.badminton.dto.VerifyEmailResponseDto;
import edu.university.badminton.model.EmailVerificationToken;
import edu.university.badminton.model.Role;
import edu.university.badminton.model.Student;
import edu.university.badminton.repository.EmailVerificationTokenRepository;
import edu.university.badminton.repository.StudentRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.NoSuchElementException;

/**
 * Service orchestrating student registration, domain validation, password hashing,
 * and single-use email verification.
 */
@Service
public class StudentRegistrationService {

    private final StudentRepository studentRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final RegistrationProperties registrationProperties;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public StudentRegistrationService(StudentRepository studentRepository,
                                      EmailVerificationTokenRepository emailVerificationTokenRepository,
                                      RegistrationProperties registrationProperties,
                                      PasswordEncoder passwordEncoder,
                                      EmailService emailService,
                                      Clock clock) {
        this.studentRepository = studentRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.registrationProperties = registrationProperties;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.clock = clock;
    }

    /**
     * Registers a new student, hashes their password, and creates a secure email verification token.
     *
     * @param request student registration payload
     * @return response indicating registration is pending email verification
     */
    @Transactional
    public RegistrationResponseDto register(RegisterStudentRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Registration request cannot be null.");
        }
        if (request.fullName() == null || request.fullName().trim().length() < 2) {
            throw new IllegalArgumentException("Full name is required (minimum 2 characters).");
        }
        if (request.universityEmail() == null || request.universityEmail().isBlank()) {
            throw new IllegalArgumentException("University email is required.");
        }
        if (request.admissionNumber() == null || request.admissionNumber().trim().length() < 3) {
            throw new IllegalArgumentException("Admission number is required (minimum 3 characters).");
        }
        if (request.password() == null || request.password().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters in length.");
        }

        // Normalize email (trimmed, lowercase)
        String normalizedEmail = request.universityEmail().trim().toLowerCase(Locale.ROOT);

        // Validate university domain
        if (!registrationProperties.isDomainAllowed(normalizedEmail)) {
            throw new IllegalArgumentException(String.format(
                    "Only students with university email domain '@%s' are permitted to register.",
                    registrationProperties.getAllowedEmailDomain()
            ));
        }

        // Normalize admission number (trimmed, uppercase)
        String normalizedAdmissionNumber = request.admissionNumber().trim().toUpperCase(Locale.ROOT);

        // Enforce uniqueness constraints
        if (studentRepository.existsByUniversityEmail(normalizedEmail)) {
            throw new IllegalStateException("University email is already registered.");
        }
        if (studentRepository.existsByAdmissionNumber(normalizedAdmissionNumber)) {
            throw new IllegalStateException("Admission number is already registered.");
        }

        // Create student account in unverified / inactive state with 0 credits until activated
        Student student = new Student();
        student.setFullName(request.fullName().trim());
        student.setUniversityEmail(normalizedEmail);
        student.setAdmissionNumber(normalizedAdmissionNumber);
        student.setPasswordHash(passwordEncoder.encode(request.password()));
        student.setRole(Role.STUDENT);
        student.setActive(false);
        student.setEmailVerified(false);
        student.setWeeklyCredits(0);
        student.setReliabilityRating(Student.DEFAULT_RELIABILITY_RATING);

        Student savedStudent = studentRepository.save(student);

        // Generate cryptographically random token (32 bytes = 64 hex characters)
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = HexFormat.of().formatHex(tokenBytes);

        // Store only the SHA-256 hash in the database
        String tokenHash = hashToken(rawToken);
        Instant expiresAt = clock.instant().plus(registrationProperties.getVerificationTokenExpiryHours(), ChronoUnit.HOURS);

        EmailVerificationToken verificationToken = new EmailVerificationToken(savedStudent, tokenHash, expiresAt);
        emailVerificationTokenRepository.save(verificationToken);

        // Dispatch verification email
        emailService.sendVerificationEmail(savedStudent.getUniversityEmail(), savedStudent.getFullName(), rawToken);

        return new RegistrationResponseDto(
                savedStudent.getId(),
                savedStudent.getFullName(),
                savedStudent.getUniversityEmail(),
                savedStudent.getAdmissionNumber(),
                false,
                "Registration successful. Please check your university email to verify your account."
        );
    }

    /**
     * Verifies a student's email using the raw token, activates the student account,
     * and initializes weekly credits to 6.
     *
     * @param rawToken the raw unhashed verification token
     * @return response confirming account activation and credit initialization
     */
    @Transactional
    public VerifyEmailResponseDto verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Verification token cannot be null or blank.");
        }

        String tokenHash = hashToken(rawToken.trim());
        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new NoSuchElementException("Invalid or unknown verification token."));

        if (token.isUsed()) {
            throw new IllegalStateException("Verification token has already been used.");
        }

        Instant now = clock.instant();
        if (token.isExpired(now)) {
            throw new IllegalStateException("Verification token has expired.");
        }

        // Mark token as used
        token.markUsed(now);
        emailVerificationTokenRepository.save(token);

        // Activate student and initialize weekly credits to 6
        Student student = token.getStudent();
        student.setEmailVerified(true);
        student.setEmailVerifiedAt(now);
        student.setActive(true);
        student.setWeeklyCredits(Student.DEFAULT_WEEKLY_CREDITS);
        studentRepository.save(student);

        return new VerifyEmailResponseDto(
                student.getId(),
                student.getFullName(),
                student.getUniversityEmail(),
                true,
                student.getWeeklyCredits(),
                "University email verified successfully. Your account is now active with 6 weekly credits."
        );
    }

    /**
     * Computes the SHA-256 hex digest of a raw token for secure database storage and comparison.
     */
    public static String hashToken(String rawToken) {
        if (rawToken == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm not available", e);
        }
    }
}
