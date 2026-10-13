package edu.university.badminton.service;

import edu.university.badminton.config.JwtService;
import edu.university.badminton.config.JwtTokenProperties;
import edu.university.badminton.dto.LoginRequestDto;
import edu.university.badminton.dto.LoginResponseDto;
import edu.university.badminton.model.Role;
import edu.university.badminton.model.Student;
import edu.university.badminton.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private JwtTokenProperties properties;
    private AuthenticationService authenticationService;
    private Student verifiedStudent;

    @BeforeEach
    void setUp() {
        properties = new JwtTokenProperties();
        properties.setExpiryHours(24);
        authenticationService = new AuthenticationService(studentRepository, passwordEncoder, jwtService, properties);

        verifiedStudent = new Student("Hamsa Hrudai", "hamsa@vitapstudent.ac.in", "23MIC7333", Role.STUDENT);
        verifiedStudent.setId(1L);
        verifiedStudent.setPasswordHash("$2a$10$hashedPassword...");
        verifiedStudent.setEmailVerified(true);
        verifiedStudent.setActive(true);
        verifiedStudent.setWeeklyCredits(6);
        verifiedStudent.setReliabilityRating(100.0);
    }

    @Test
    @DisplayName("Authenticates successfully using university email and returns JWT response")
    void shouldLoginSuccessfullyWithEmail() {
        when(studentRepository.findByUniversityEmail("hamsa@vitapstudent.ac.in"))
                .thenReturn(Optional.of(verifiedStudent));
        when(passwordEncoder.matches("ValidPassword123!", "$2a$10$hashedPassword..."))
                .thenReturn(true);
        when(jwtService.generateToken(verifiedStudent))
                .thenReturn("mock.jwt.token.123");

        LoginRequestDto request = new LoginRequestDto("hamsa@vitapstudent.ac.in", "ValidPassword123!");
        LoginResponseDto response = authenticationService.login(request);

        assertThat(response.token()).isEqualTo("mock.jwt.token.123");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.email()).isEqualTo("hamsa@vitapstudent.ac.in");
        assertThat(response.role()).isEqualTo(Role.STUDENT);
        assertThat(response.expiresInSeconds()).isEqualTo(86400L);
    }

    @Test
    @DisplayName("Authenticates successfully using admission number")
    void shouldLoginSuccessfullyWithAdmissionNumber() {
        when(studentRepository.findByUniversityEmail("23mic7333"))
                .thenReturn(Optional.empty());
        when(studentRepository.findByAdmissionNumber("23MIC7333"))
                .thenReturn(Optional.of(verifiedStudent));
        when(passwordEncoder.matches("ValidPassword123!", "$2a$10$hashedPassword..."))
                .thenReturn(true);
        when(jwtService.generateToken(verifiedStudent))
                .thenReturn("mock.jwt.token.456");

        LoginRequestDto request = new LoginRequestDto("23MIC7333", "ValidPassword123!");
        LoginResponseDto response = authenticationService.login(request);

        assertThat(response.token()).isEqualTo("mock.jwt.token.456");
        assertThat(response.admissionNumber()).isEqualTo("23MIC7333");
    }

    @Test
    @DisplayName("Throws BadCredentialsException on incorrect password")
    void shouldThrowBadCredentialsOnWrongPassword() {
        when(studentRepository.findByUniversityEmail("hamsa@vitapstudent.ac.in"))
                .thenReturn(Optional.of(verifiedStudent));
        when(passwordEncoder.matches("WrongPassword", "$2a$10$hashedPassword..."))
                .thenReturn(false);

        LoginRequestDto request = new LoginRequestDto("hamsa@vitapstudent.ac.in", "WrongPassword");

        assertThatThrownBy(() -> authenticationService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid university email/admission number or password.");
    }

    @Test
    @DisplayName("Throws BadCredentialsException when student identifier is not found")
    void shouldThrowBadCredentialsOnUnknownUser() {
        when(studentRepository.findByUniversityEmail("unknown@vitapstudent.ac.in"))
                .thenReturn(Optional.empty());
        when(studentRepository.findByAdmissionNumber(any()))
                .thenReturn(Optional.empty());

        LoginRequestDto request = new LoginRequestDto("unknown@vitapstudent.ac.in", "Password123!");

        assertThatThrownBy(() -> authenticationService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid university email/admission number or password.");
    }

    @Test
    @DisplayName("Throws DisabledException when student account is inactive")
    void shouldThrowDisabledExceptionWhenInactive() {
        verifiedStudent.setActive(false);

        when(studentRepository.findByUniversityEmail("hamsa@vitapstudent.ac.in"))
                .thenReturn(Optional.of(verifiedStudent));
        when(passwordEncoder.matches("ValidPassword123!", "$2a$10$hashedPassword..."))
                .thenReturn(true);

        LoginRequestDto request = new LoginRequestDto("hamsa@vitapstudent.ac.in", "ValidPassword123!");

        assertThatThrownBy(() -> authenticationService.login(request))
                .isInstanceOf(DisabledException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    @DisplayName("Throws IllegalStateException when student email is not yet verified")
    void shouldThrowIllegalStateExceptionWhenUnverified() {
        verifiedStudent.setEmailVerified(false);

        when(studentRepository.findByUniversityEmail("hamsa@vitapstudent.ac.in"))
                .thenReturn(Optional.of(verifiedStudent));
        when(passwordEncoder.matches("ValidPassword123!", "$2a$10$hashedPassword..."))
                .thenReturn(true);

        LoginRequestDto request = new LoginRequestDto("hamsa@vitapstudent.ac.in", "ValidPassword123!");

        assertThatThrownBy(() -> authenticationService.login(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not verified");
    }
}
