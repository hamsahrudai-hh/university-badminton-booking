package edu.university.badminton.service;

import edu.university.badminton.config.JwtService;
import edu.university.badminton.config.JwtTokenProperties;
import edu.university.badminton.dto.LoginRequestDto;
import edu.university.badminton.dto.LoginResponseDto;
import edu.university.badminton.model.Role;
import edu.university.badminton.model.Student;
import edu.university.badminton.repository.StudentRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * Service managing user authentication, BCrypt verification, and JWT issuance.
 */
@Service
public class AuthenticationService {

    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtTokenProperties jwtTokenProperties;

    public AuthenticationService(StudentRepository studentRepository,
                                 PasswordEncoder passwordEncoder,
                                 JwtService jwtService,
                                 JwtTokenProperties jwtTokenProperties) {
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtTokenProperties = jwtTokenProperties;
    }

    /**
     * Authenticates a user by university email or admission number and password.
     */
    @Transactional(readOnly = true)
    public LoginResponseDto login(LoginRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Login request body is required.");
        }
        if (request.identifier() == null || request.identifier().isBlank()) {
            throw new IllegalArgumentException("University email or admission number is required.");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new IllegalArgumentException("Password is required.");
        }

        String raw = request.identifier().trim();
        Optional<Student> studentOpt = studentRepository.findByUniversityEmail(raw.toLowerCase(Locale.ROOT))
                .or(() -> studentRepository.findByAdmissionNumber(raw.toUpperCase(Locale.ROOT)))
                .or(() -> studentRepository.findByAdmissionNumber(raw));

        if (studentOpt.isEmpty()) {
            throw new BadCredentialsException("Invalid university email/admission number or password.");
        }

        Student student = studentOpt.get();

        if (student.getPasswordHash() == null || student.getPasswordHash().isBlank()) {
            throw new BadCredentialsException("Invalid university email/admission number or password.");
        }

        if (!passwordEncoder.matches(request.password(), student.getPasswordHash())) {
            throw new BadCredentialsException("Invalid university email/admission number or password.");
        }

        if (!Boolean.TRUE.equals(student.getActive())) {
            throw new DisabledException("Account is inactive. Please contact administration.");
        }

        // Enforce email verification for students
        if (student.getRole() == Role.STUDENT && !Boolean.TRUE.equals(student.getEmailVerified())) {
            throw new IllegalStateException("University email is not verified. Please verify your email before logging in.");
        }

        String token = jwtService.generateToken(student);
        long expiresInSeconds = (long) jwtTokenProperties.getExpiryHours() * 3600;

        return new LoginResponseDto(
                token,
                "Bearer",
                expiresInSeconds,
                student.getId(),
                student.getFullName(),
                student.getUniversityEmail(),
                student.getAdmissionNumber(),
                student.getRole(),
                student.getWeeklyCredits(),
                student.getReliabilityRating()
        );
    }
}
