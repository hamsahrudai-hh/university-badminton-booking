package edu.university.badminton.service;

import edu.university.badminton.dto.CreateBookingRequestDto;
import edu.university.badminton.dto.JoinQueueRequestDto;
import edu.university.badminton.dto.RegisterStudentRequestDto;
import edu.university.badminton.dto.RegistrationResponseDto;
import edu.university.badminton.dto.StudentResponseDto;
import edu.university.badminton.dto.VerifyEmailResponseDto;
import edu.university.badminton.model.BookingQueue;
import edu.university.badminton.model.Court;
import edu.university.badminton.model.EmailVerificationToken;
import edu.university.badminton.model.QueueEntry;
import edu.university.badminton.model.Role;
import edu.university.badminton.model.SessionConfig;
import edu.university.badminton.model.SessionType;
import edu.university.badminton.model.SlotStatus;
import edu.university.badminton.model.Student;
import edu.university.badminton.model.TimeSlot;
import edu.university.badminton.repository.BookingPlayerRepository;
import edu.university.badminton.repository.BookingQueueRepository;
import edu.university.badminton.repository.BookingRepository;
import edu.university.badminton.repository.CourtRepository;
import edu.university.badminton.repository.EmailVerificationTokenRepository;
import edu.university.badminton.repository.QueueEntryRepository;
import edu.university.badminton.repository.SessionConfigRepository;
import edu.university.badminton.repository.StudentRepository;
import edu.university.badminton.repository.TimeSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class StudentRegistrationIntegrationTest {

    @Autowired
    private StudentRegistrationService registrationService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingQueueService bookingQueueService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private EmailVerificationTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private BookingPlayerRepository bookingPlayerRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private QueueEntryRepository queueEntryRepository;

    @Autowired
    private BookingQueueRepository bookingQueueRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private CourtRepository courtRepository;

    @Autowired
    private SessionConfigRepository sessionConfigRepository;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        bookingPlayerRepository.deleteAll();
        bookingRepository.deleteAll();
        queueEntryRepository.deleteAll();
        bookingQueueRepository.deleteAll();
        timeSlotRepository.deleteAll();
        courtRepository.deleteAll();
        sessionConfigRepository.deleteAll();
        studentRepository.deleteAll();
    }

    @Test
    @DisplayName("Registers student: hashes password, stores unverified status with 0 credits, generates token hash")
    void shouldRegisterStudentSuccessfully() {
        RegisterStudentRequestDto request = new RegisterStudentRequestDto(
                "  Vijay Kumar  ",
                "  VIJAY.KUMAR@VITAPSTUDENT.AC.IN  ",
                "  24bcs101  ",
                "StrongPassword#123"
        );

        RegistrationResponseDto response = registrationService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.studentId()).isNotNull();
        assertThat(response.fullName()).isEqualTo("Vijay Kumar");
        assertThat(response.universityEmail()).isEqualTo("vijay.kumar@vitapstudent.ac.in");
        assertThat(response.admissionNumber()).isEqualTo("24BCS101");
        assertThat(response.emailVerified()).isFalse();

        // Verify Student entity state in database
        Student student = studentRepository.findById(response.studentId()).orElseThrow();
        assertThat(student.getFullName()).isEqualTo("Vijay Kumar");
        assertThat(student.getUniversityEmail()).isEqualTo("vijay.kumar@vitapstudent.ac.in");
        assertThat(student.getAdmissionNumber()).isEqualTo("24BCS101");
        assertThat(student.getEmailVerified()).isFalse();
        assertThat(student.getActive()).isFalse();
        assertThat(student.getWeeklyCredits()).isEqualTo(0);

        // Password must be hashed with BCrypt and must match original password
        assertThat(student.getPasswordHash()).isNotNull();
        assertThat(student.getPasswordHash()).startsWith("$2");
        assertThat(student.getPasswordHash()).isNotEqualTo("StrongPassword#123");
        assertThat(passwordEncoder.matches("StrongPassword#123", student.getPasswordHash())).isTrue();

        // Verify verification token created in database
        List<EmailVerificationToken> tokens = tokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        EmailVerificationToken savedToken = tokens.getFirst();
        assertThat(savedToken.getStudent().getId()).isEqualTo(student.getId());
        assertThat(savedToken.isUsed()).isFalse();
        assertThat(savedToken.getTokenHash()).matches("^[0-9a-f]{64}$");
        assertThat(savedToken.getExpiresAt()).isAfter(Instant.now());

        // Unverified student must NOT appear in active students list
        List<StudentResponseDto> activeStudents = studentService.getAllActiveStudents();
        assertThat(activeStudents).noneMatch(s -> s.id().equals(student.getId()));
    }

    @Test
    @DisplayName("Rejects registration with non-university email domain")
    void shouldRejectInvalidEmailDomain() {
        RegisterStudentRequestDto request = new RegisterStudentRequestDto(
                "John Doe",
                "john.doe@gmail.com",
                "24BCS102",
                "Password123"
        );

        assertThatThrownBy(() -> registrationService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only students with university email domain '@vitapstudent.ac.in' are permitted to register.");

        assertThat(studentRepository.count()).isZero();
    }

    @Test
    @DisplayName("Rejects registration with duplicate university email or admission number")
    void shouldRejectDuplicateEmailOrAdmissionNumber() {
        RegisterStudentRequestDto request1 = new RegisterStudentRequestDto(
                "Student One",
                "student1@vitapstudent.ac.in",
                "24BCS103",
                "Password123"
        );
        registrationService.register(request1);

        // Duplicate email (different case/spacing)
        RegisterStudentRequestDto dupEmailRequest = new RegisterStudentRequestDto(
                "Student Duplicate",
                "  STUDENT1@vitapstudent.ac.in ",
                "24BCS999",
                "Password123"
        );
        assertThatThrownBy(() -> registrationService.register(dupEmailRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("University email is already registered.");

        // Duplicate admission number
        RegisterStudentRequestDto dupAdmissionRequest = new RegisterStudentRequestDto(
                "Student Duplicate Adm",
                "student2@vitapstudent.ac.in",
                "  24bcs103  ",
                "Password123"
        );
        assertThatThrownBy(() -> registrationService.register(dupAdmissionRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Admission number is already registered.");
    }

    @Test
    @DisplayName("Prevents unverified student from joining booking queue")
    void shouldPreventUnverifiedStudentFromJoiningQueue() {
        Student unverifiedStudent = new Student("Unverified", "unverified@vitapstudent.ac.in", "24BCS104", Role.STUDENT);
        unverifiedStudent.setEmailVerified(false);
        unverifiedStudent.setActive(true);
        unverifiedStudent.setWeeklyCredits(6);
        unverifiedStudent = studentRepository.save(unverifiedStudent);

        SessionConfig sessionConfig = sessionConfigRepository.save(
                new SessionConfig(SessionType.MORNING, LocalTime.of(6, 0), LocalTime.of(8, 30), 30, LocalTime.of(21, 0), LocalTime.of(21, 5), 1)
        );
        LocalDate sessionDate = LocalDate.of(2026, 10, 20);
        bookingQueueRepository.save(new BookingQueue(sessionConfig, SessionType.MORNING, sessionDate));

        JoinQueueRequestDto request = new JoinQueueRequestDto(unverifiedStudent.getId(), sessionDate, SessionType.MORNING);

        assertThatThrownBy(() -> bookingQueueService.joinQueue(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Student account has not verified university email. Unverified students cannot join the booking queue.");
    }

    @Test
    @DisplayName("Prevents unverified student from being included in a booking transaction")
    void shouldPreventUnverifiedStudentFromBooking() {
        Student verifiedLead = new Student("Verified Lead", "lead@vitapstudent.ac.in", "24BCS105", Role.STUDENT);
        verifiedLead.setEmailVerified(true);
        verifiedLead.setActive(true);
        verifiedLead.setWeeklyCredits(6);
        verifiedLead = studentRepository.save(verifiedLead);

        Student unverifiedPeer = new Student("Unverified Peer", "peer@vitapstudent.ac.in", "24BCS106", Role.STUDENT);
        unverifiedPeer.setEmailVerified(false);
        unverifiedPeer.setActive(true);
        unverifiedPeer.setWeeklyCredits(6);
        unverifiedPeer = studentRepository.save(unverifiedPeer);

        Court court = courtRepository.save(new Court(1));
        SessionConfig sessionConfig = sessionConfigRepository.save(
                new SessionConfig(SessionType.MORNING, LocalTime.of(6, 0), LocalTime.of(8, 30), 30, LocalTime.of(21, 0), LocalTime.of(21, 5), 1)
        );
        LocalDate date = LocalDate.of(2026, 10, 20);
        TimeSlot slot = timeSlotRepository.save(
                new TimeSlot(court, sessionConfig, date, LocalDateTime.of(date, LocalTime.of(6, 0)), LocalDateTime.of(date, LocalTime.of(6, 30)), SlotStatus.AVAILABLE)
        );
        BookingQueue queue = bookingQueueRepository.save(new BookingQueue(sessionConfig, SessionType.MORNING, date));
        QueueEntry queueEntry = new QueueEntry(queue, verifiedLead, Instant.now());
        queueEntry.admit(Instant.now());
        queueEntryRepository.save(queueEntry);

        CreateBookingRequestDto request = CreateBookingRequestDto.of(
                verifiedLead.getId(),
                slot.getId(),
                List.of(verifiedLead.getId(), unverifiedPeer.getId()),
                "unverified-test-idemp"
        );

        assertThatThrownBy(() -> bookingService.confirmBooking(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has not verified their university email");

        // Confirm lead credits were NOT deducted due to rollback
        Student refreshedLead = studentRepository.findById(verifiedLead.getId()).orElseThrow();
        assertThat(refreshedLead.getWeeklyCredits()).isEqualTo(6);
        // Confirm slot was NOT booked
        TimeSlot refreshedSlot = timeSlotRepository.findById(slot.getId()).orElseThrow();
        assertThat(refreshedSlot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Email verification activates account, initializes 6 credits, marks token used, and prevents token reuse")
    void shouldVerifyEmailAndActivateStudent() {
        Student student = new Student("Priya Sharma", "priya@vitapstudent.ac.in", "24BCS107", Role.STUDENT);
        student.setEmailVerified(false);
        student.setActive(false);
        student.setWeeklyCredits(0);
        student = studentRepository.save(student);

        String rawToken = "raw-integration-test-token-abcdef1234567890";
        String tokenHash = StudentRegistrationService.hashToken(rawToken);

        EmailVerificationToken token = new EmailVerificationToken(student, tokenHash, Instant.now().plus(24, ChronoUnit.HOURS));
        tokenRepository.save(token);

        VerifyEmailResponseDto response = registrationService.verifyEmail(rawToken);

        assertThat(response).isNotNull();
        assertThat(response.studentId()).isEqualTo(student.getId());
        assertThat(response.emailVerified()).isTrue();
        assertThat(response.weeklyCredits()).isEqualTo(6);

        // Verify student entity in DB
        Student refreshedStudent = studentRepository.findById(student.getId()).orElseThrow();
        assertThat(refreshedStudent.getEmailVerified()).isTrue();
        assertThat(refreshedStudent.getActive()).isTrue();
        assertThat(refreshedStudent.getWeeklyCredits()).isEqualTo(6);
        assertThat(refreshedStudent.getEmailVerifiedAt()).isNotNull();

        // Verify token in DB is marked used
        EmailVerificationToken refreshedToken = tokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(refreshedToken.isUsed()).isTrue();
        assertThat(refreshedToken.getUsedAt()).isNotNull();

        // Verified student now appears in active students list
        Long savedStudentId = student.getId();
        List<StudentResponseDto> activeStudents = studentService.getAllActiveStudents();
        assertThat(activeStudents).anyMatch(s -> s.id().equals(savedStudentId) && s.emailVerified());

        // Attempting to reuse the same token must fail
        assertThatThrownBy(() -> registrationService.verifyEmail(rawToken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Verification token has already been used.");
    }

    @Test
    @DisplayName("Expired token cannot be used to verify email")
    void shouldRejectExpiredToken() {
        Student student = new Student("Kiran", "kiran@vitapstudent.ac.in", "24BCS108", Role.STUDENT);
        student.setEmailVerified(false);
        student.setActive(false);
        student.setWeeklyCredits(0);
        student = studentRepository.save(student);

        String rawToken = "expired-token-integration";
        String tokenHash = StudentRegistrationService.hashToken(rawToken);

        EmailVerificationToken token = new EmailVerificationToken(student, tokenHash, Instant.now().minus(1, ChronoUnit.HOURS));
        tokenRepository.save(token);

        assertThatThrownBy(() -> registrationService.verifyEmail(rawToken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Verification token has expired.");

        Student refreshed = studentRepository.findById(student.getId()).orElseThrow();
        assertThat(refreshed.getEmailVerified()).isFalse();
        assertThat(refreshed.getActive()).isFalse();
        assertThat(refreshed.getWeeklyCredits()).isEqualTo(0);
    }

    @Test
    @DisplayName("Unknown token throws NoSuchElementException")
    void shouldRejectUnknownToken() {
        assertThatThrownBy(() -> registrationService.verifyEmail("completely-unknown-token"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Invalid or unknown verification token.");
    }
}
