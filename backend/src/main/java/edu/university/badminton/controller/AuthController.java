package edu.university.badminton.controller;

import edu.university.badminton.dto.LoginRequestDto;
import edu.university.badminton.dto.LoginResponseDto;
import edu.university.badminton.dto.RegisterStudentRequestDto;
import edu.university.badminton.dto.RegistrationResponseDto;
import edu.university.badminton.dto.VerifyEmailRequestDto;
import edu.university.badminton.dto.VerifyEmailResponseDto;
import edu.university.badminton.service.AuthenticationService;
import edu.university.badminton.service.StudentRegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * REST controller for student account registration, email verification, and authentication.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final StudentRegistrationService registrationService;
    private final AuthenticationService authenticationService;

    public AuthController(StudentRegistrationService registrationService,
                          AuthenticationService authenticationService) {
        this.registrationService = registrationService;
        this.authenticationService = authenticationService;
    }

    /**
     * Authenticates an existing student or staff user via university email or admission number and password.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody(required = false) LoginRequestDto request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Login request body is required."));
        }
        try {
            LoginResponseDto response = authenticationService.login(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (DisabledException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Registers a new student account.
     * Enforces university domain validation (@vitapstudent.ac.in), uniqueness, and password length.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody(required = false) RegisterStudentRequestDto request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Registration request body is required."));
        }
        try {
            RegistrationResponseDto response = registrationService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Verifies a student's university email address via POST with JSON body containing token.
     */
    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmailPost(@RequestBody(required = false) VerifyEmailRequestDto request) {
        if (request == null || request.token() == null || request.token().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Verification token is required."));
        }
        return processVerification(request.token());
    }

    /**
     * Verifies a student's university email address via GET with query parameter token
     * (convenient for clickable verification links in emails).
     */
    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmailGet(@RequestParam(name = "token", required = false) String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Verification token query parameter is required."));
        }
        return processVerification(token);
    }

    private ResponseEntity<?> processVerification(String rawToken) {
        try {
            VerifyEmailResponseDto response = registrationService.verifyEmail(rawToken);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
