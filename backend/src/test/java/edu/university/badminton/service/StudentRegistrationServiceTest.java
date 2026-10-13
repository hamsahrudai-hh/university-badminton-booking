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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentRegistrationServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    private RegistrationProperties properties;
    private Clock fixedClock;
    private StudentRegistrationService registrationService;

    private final Instant now = Instant.parse("2026-10-15T10:00:00Z");

    @BeforeEach
    void setUp() {
        properties = new RegistrationProperties("vitapstudent.ac.in", 24);
        fixedClock = Clock.fixed(now, ZoneId.of("UTC"));
        registrationService = new StudentRegistrationService(
                studentRepository,
                emailVerificationTokenRepository,
                properties,
                passwordEncoder,
                emailService,
                fixedClock
        );
    }

    @Test
    @DisplayName("Should accept valid registration with whitespace and uppercase in vitapstudent.ac.in email")
    void shouldRegisterStudentWithNormalizedEmail() {
        RegisterStudentRequestDto request = new RegisterStudentRequestDto(
                "Jane Doe",
                "  JANE.DOE@VITAPSTUDENT.AC.IN  ",
                " 24bcs101 ",
                "SecurePassword123"
        );

        when(studentRepository.existsByUniversityEmail("jane.doe@vitapstudent.ac.in")).thenReturn(false);
        when(studentRepository.existsByAdmissionNumber("24BCS101")).thenReturn(false);
        when(passwordEncoder.encode("SecurePassword123")).thenReturn("BCRYPT_HASH_xyz");

        Student savedStudent = new Student("Jane Doe", "jane.doe@vitapstudent.ac.in", "24BCS101", Role.STUDENT);
        savedStudent.setId(42L);
        savedStudent.setActive(false);
        savedStudent.setEmailVerified(false);
        savedStudent.setWeeklyCredits(0);
        when(studentRepository.save(any(Student.class))).thenReturn(savedStudent);

        RegistrationResponseDto response = registrationService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.studentId()).isEqualTo(42L);
        assertThat(response.universityEmail()).isEqualTo("jane.doe@vitapstudent.ac.in");
        assertThat(response.admissionNumber()).isEqualTo("24BCS101");
        assertThat(response.emailVerified()).isFalse();

        // Verify password was hashed and never passed plaintext
        ArgumentCaptor<Student> studentCaptor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(studentCaptor.capture());
        Student capturedStudent = studentCaptor.getValue();
        assertThat(capturedStudent.getPasswordHash()).isEqualTo("BCRYPT_HASH_xyz");
        assertThat(capturedStudent.getPasswordHash()).doesNotContain("SecurePassword123");
        assertThat(capturedStudent.getActive()).isFalse();
        assertThat(capturedStudent.getEmailVerified()).isFalse();
        assertThat(capturedStudent.getWeeklyCredits()).isEqualTo(0);

        // Verify token creation and email dispatch
        ArgumentCaptor<EmailVerificationToken> tokenCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(emailVerificationTokenRepository).save(tokenCaptor.capture());
        EmailVerificationToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getTokenHash()).hasSize(64); // SHA-256 hex length
        assertThat(savedToken.isUsed()).isFalse();
        assertThat(savedToken.getExpiresAt()).isEqualTo(now.plusSeconds(24 * 3600));

        verify(emailService).sendVerificationEmail(eq("jane.doe@vitapstudent.ac.in"), eq("Jane Doe"), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jane@gmail.com",
            "jane@vitap.ac.in",
            "jane@otheruniversity.edu",
            "jane@vitapstudent.com",
            "@vitapstudent.ac.in"
    })
    @DisplayName("Should reject registrations from non-permitted email domains")
    void shouldRejectInvalidEmailDomains(String invalidEmail) {
        RegisterStudentRequestDto request = new RegisterStudentRequestDto(
                "Jane Doe", invalidEmail, "24BCS101", "SecurePassword123"
        );

        assertThatThrownBy(() -> registrationService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only students with university email domain '@vitapstudent.ac.in' are permitted");

        verify(studentRepository, never()).save(any());
        verify(emailVerificationTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject registration if university email already exists")
    void shouldRejectDuplicateEmail() {
        RegisterStudentRequestDto request = new RegisterStudentRequestDto(
                "Jane Doe", "jane.doe@vitapstudent.ac.in", "24BCS101", "SecurePassword123"
        );
        when(studentRepository.existsByUniversityEmail("jane.doe@vitapstudent.ac.in")).thenReturn(true);

        assertThatThrownBy(() -> registrationService.register(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("University email is already registered");
    }

    @Test
    @DisplayName("Should reject registration if admission number already exists")
    void shouldRejectDuplicateAdmissionNumber() {
        RegisterStudentRequestDto request = new RegisterStudentRequestDto(
                "Jane Doe", "jane.doe@vitapstudent.ac.in", "24BCS101", "SecurePassword123"
        );
        when(studentRepository.existsByUniversityEmail("jane.doe@vitapstudent.ac.in")).thenReturn(false);
        when(studentRepository.existsByAdmissionNumber("24BCS101")).thenReturn(true);

        assertThatThrownBy(() -> registrationService.register(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Admission number is already registered");
    }

    @Test
    @DisplayName("Should reject registration if password is fewer than 8 characters")
    void shouldRejectShortPassword() {
        RegisterStudentRequestDto request = new RegisterStudentRequestDto(
                "Jane Doe", "jane.doe@vitapstudent.ac.in", "24BCS101", "short"
        );

        assertThatThrownBy(() -> registrationService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password must be at least 8 characters");
    }

    @Test
    @DisplayName("Should successfully verify email, activate student, and initialize weekly credits to 6")
    void shouldVerifyEmailAndInitializeCredits() {
        String rawToken = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        String tokenHash = StudentRegistrationService.hashToken(rawToken);

        Student student = new Student("Alice", "alice@vitapstudent.ac.in", "24BCS001", Role.STUDENT);
        student.setId(10L);
        student.setActive(false);
        student.setEmailVerified(false);
        student.setWeeklyCredits(0);

        EmailVerificationToken token = new EmailVerificationToken(student, tokenHash, now.plusSeconds(3600));

        when(emailVerificationTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        VerifyEmailResponseDto response = registrationService.verifyEmail(rawToken);

        assertThat(response).isNotNull();
        assertThat(response.studentId()).isEqualTo(10L);
        assertThat(response.emailVerified()).isTrue();
        assertThat(response.weeklyCredits()).isEqualTo(6);

        assertThat(student.getEmailVerified()).isTrue();
        assertThat(student.getActive()).isTrue();
        assertThat(student.getWeeklyCredits()).isEqualTo(6);
        assertThat(student.getEmailVerifiedAt()).isEqualTo(now);
        assertThat(token.isUsed()).isTrue();
        assertThat(token.getUsedAt()).isEqualTo(now);

        verify(studentRepository).save(student);
        verify(emailVerificationTokenRepository).save(token);
    }

    @Test
    @DisplayName("Should reject email verification with expired token")
    void shouldRejectExpiredToken() {
        String rawToken = "test_expired_token";
        String tokenHash = StudentRegistrationService.hashToken(rawToken);

        Student student = new Student("Alice", "alice@vitapstudent.ac.in", "24BCS001", Role.STUDENT);
        student.setEmailVerified(false);
        student.setActive(false);
        // Expired 1 hour ago
        EmailVerificationToken token = new EmailVerificationToken(student, tokenHash, now.minusSeconds(3600));

        when(emailVerificationTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> registrationService.verifyEmail(rawToken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Verification token has expired");

        assertThat(student.getEmailVerified()).isFalse();
        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject email verification with already used token")
    void shouldRejectAlreadyUsedToken() {
        String rawToken = "test_used_token";
        String tokenHash = StudentRegistrationService.hashToken(rawToken);

        Student student = new Student("Alice", "alice@vitapstudent.ac.in", "24BCS001", Role.STUDENT);
        EmailVerificationToken token = new EmailVerificationToken(student, tokenHash, now.plusSeconds(3600));
        token.markUsed(now.minusSeconds(10));

        when(emailVerificationTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> registrationService.verifyEmail(rawToken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Verification token has already been used");

        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject verification if token does not exist")
    void shouldRejectUnknownToken() {
        String rawToken = "unknown_token";
        String tokenHash = StudentRegistrationService.hashToken(rawToken);

        when(emailVerificationTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrationService.verifyEmail(rawToken))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Invalid or unknown verification token");
    }
}
