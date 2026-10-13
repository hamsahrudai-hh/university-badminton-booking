package edu.university.badminton.service;

import edu.university.badminton.config.JwtClaims;
import edu.university.badminton.config.JwtService;
import edu.university.badminton.dto.AdmitNextResponseDto;
import edu.university.badminton.dto.BookingResponseDto;
import edu.university.badminton.dto.CheckInRequestDto;
import edu.university.badminton.dto.CheckInResponseDto;
import edu.university.badminton.dto.CreateBookingRequestDto;
import edu.university.badminton.dto.JoinQueueRequestDto;
import edu.university.badminton.dto.LoginRequestDto;
import edu.university.badminton.dto.LoginResponseDto;
import edu.university.badminton.dto.NoShowProcessingResponseDto;
import edu.university.badminton.dto.QueueEntryResponseDto;
import edu.university.badminton.model.BookingPlayer;
import edu.university.badminton.model.Court;
import edu.university.badminton.model.QueueEntry;
import edu.university.badminton.model.QueueEntryStatus;
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
import edu.university.badminton.repository.QueueEntryRepository;
import edu.university.badminton.repository.SessionConfigRepository;
import edu.university.badminton.repository.StudentRepository;
import edu.university.badminton.repository.TimeSlotRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Complete End-to-End student booking journey integration test:
 * Student Login -> Queue Admission -> Group Booking -> Individual QR Passes -> Staff Check-In -> No-Show Processing.
 * <p>
 * Evaluates transactional atomicity, deterministic clock boundaries (Asia/Kolkata),
 * credit deductions, QR security, and idempotent staff check-in in an isolated test environment.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:badminton_e2e_testdb;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
@Import(BookingJourneyEndToEndIntegrationTest.TestClockConfiguration.class)
class BookingJourneyEndToEndIntegrationTest {

    private static final ZoneId UNIVERSITY_ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalDate SESSION_DATE = LocalDate.of(2026, 10, 15);

    @TestConfiguration
    static class TestClockConfiguration {
        @Bean
        @Primary
        public MutableTestClock mutableTestClock() {
            LocalDateTime initialTime = LocalDateTime.of(SESSION_DATE.minusDays(1), LocalTime.of(20, 50, 0));
            return new MutableTestClock(initialTime.atZone(UNIVERSITY_ZONE).toInstant(), UNIVERSITY_ZONE);
        }
    }

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingPlayerRepository bookingPlayerRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourtRepository courtRepository;

    @Autowired
    private SessionConfigRepository sessionConfigRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private BookingQueueRepository bookingQueueRepository;

    @Autowired
    private QueueEntryRepository queueEntryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private MutableTestClock testClock;

    @Autowired
    private BookingQueueService bookingQueueService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private CheckInService checkInService;

    private Student aliceHost;
    private Student bobMember;
    private Student charlieMember;
    private Student danaZeroCredit;
    private Student staffSam;

    private Court court1;
    private SessionConfig morningConfig;
    private TimeSlot morningSlot;

    @BeforeEach
    void setUp() {
        // Start clock on previous evening at 20:50 IST (10 minutes before queue opens at 21:00)
        LocalDateTime initialTime = LocalDateTime.of(SESSION_DATE.minusDays(1), LocalTime.of(20, 50, 0));
        testClock.setDateTime(initialTime);

        // Clean isolated test database
        bookingPlayerRepository.deleteAll();
        bookingRepository.deleteAll();
        queueEntryRepository.deleteAll();
        bookingQueueRepository.deleteAll();
        timeSlotRepository.deleteAll();
        courtRepository.deleteAll();
        sessionConfigRepository.deleteAll();
        studentRepository.deleteAll();

        // 1. Create registered students with verified email & initial 6 credits
        aliceHost = new Student("Alice Host", "alice@vitapstudent.ac.in", "23MIC7001", Role.STUDENT);
        aliceHost.setPasswordHash(passwordEncoder.encode("Password123!"));
        aliceHost.setEmailVerified(true);
        aliceHost.setActive(true);
        aliceHost.setWeeklyCredits(6);
        aliceHost.setReliabilityRating(100.0);
        aliceHost = studentRepository.save(aliceHost);

        bobMember = new Student("Bob Member", "bob@vitapstudent.ac.in", "23MIC7002", Role.STUDENT);
        bobMember.setPasswordHash(passwordEncoder.encode("Password123!"));
        bobMember.setEmailVerified(true);
        bobMember.setActive(true);
        bobMember.setWeeklyCredits(6);
        bobMember.setReliabilityRating(100.0);
        bobMember = studentRepository.save(bobMember);

        charlieMember = new Student("Charlie Member", "charlie@vitapstudent.ac.in", "23MIC7003", Role.STUDENT);
        charlieMember.setPasswordHash(passwordEncoder.encode("Password123!"));
        charlieMember.setEmailVerified(true);
        charlieMember.setActive(true);
        charlieMember.setWeeklyCredits(6);
        charlieMember.setReliabilityRating(100.0);
        charlieMember = studentRepository.save(charlieMember);

        danaZeroCredit = new Student("Dana Zero", "dana@vitapstudent.ac.in", "23MIC7004", Role.STUDENT);
        danaZeroCredit.setPasswordHash(passwordEncoder.encode("Password123!"));
        danaZeroCredit.setEmailVerified(true);
        danaZeroCredit.setActive(true);
        danaZeroCredit.setWeeklyCredits(0);
        danaZeroCredit.setReliabilityRating(100.0);
        danaZeroCredit = studentRepository.save(danaZeroCredit);

        staffSam = new Student("Staff Sam", "sam.staff@vitapstudent.ac.in", "STAFF_01", Role.STAFF);
        staffSam.setPasswordHash(passwordEncoder.encode("StaffPassword123!"));
        staffSam.setEmailVerified(true);
        staffSam.setActive(true);
        staffSam = studentRepository.save(staffSam);

        // 2. Setup Court 1 & Morning Session (06:00 - 08:30, queue opens 21:00 previous day, admission at 21:05)
        court1 = courtRepository.save(new Court(1));

        morningConfig = sessionConfigRepository.save(new SessionConfig(
                SessionType.MORNING,
                LocalTime.of(6, 0),
                LocalTime.of(8, 30),
                30,
                LocalTime.of(21, 0),
                LocalTime.of(21, 5),
                1
        ));

        // 3. Create Slot 1: 06:00 - 06:30 on Court 1
        LocalDateTime slotStart = LocalDateTime.of(SESSION_DATE, LocalTime.of(6, 0));
        LocalDateTime slotEnd = LocalDateTime.of(SESSION_DATE, LocalTime.of(6, 30));
        morningSlot = timeSlotRepository.save(
                new TimeSlot(court1, morningConfig, SESSION_DATE, slotStart, slotEnd, SlotStatus.AVAILABLE)
        );
    }

    @AfterEach
    void tearDown() {
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
    @DisplayName("Complete end-to-end journey: Login -> Queue Join -> Admission -> Booking -> Individual QR Passes -> Staff Check-In -> Idempotent No-Show")
    void shouldExecuteCompleteStudentBookingJourneyThroughStaffCheckIn() {
        // --- STEP 1: Student and Staff Authentication ---
        LoginResponseDto aliceLogin = authenticationService.login(new LoginRequestDto("alice@vitapstudent.ac.in", "Password123!"));
        assertThat(aliceLogin).isNotNull();
        assertThat(aliceLogin.token()).isNotBlank();
        assertThat(aliceLogin.role()).isEqualTo(Role.STUDENT);

        LoginResponseDto staffLogin = authenticationService.login(new LoginRequestDto("sam.staff@vitapstudent.ac.in", "StaffPassword123!"));
        assertThat(staffLogin).isNotNull();
        assertThat(staffLogin.token()).isNotBlank();
        assertThat(staffLogin.role()).isEqualTo(Role.STAFF);

        // Verify JWT claims are valid and non-null
        JwtClaims aliceClaims = jwtService.validateAndParseToken(aliceLogin.token());
        assertThat(aliceClaims.email()).isEqualTo("alice@vitapstudent.ac.in");
        assertThat(aliceClaims.role()).isEqualTo(Role.STUDENT);

        JwtClaims staffClaims = jwtService.validateAndParseToken(staffLogin.token());
        assertThat(staffClaims.role()).isEqualTo(Role.STAFF);

        // --- STEP 2: Queue Opening Boundaries ---
        // At 20:50 IST, queue is closed. Joining must be rejected.
        assertThatThrownBy(() -> bookingQueueService.joinQueue(new JoinQueueRequestDto(aliceHost.getId(), SESSION_DATE, SessionType.MORNING)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has not opened yet");

        // Advance clock past 21:00 IST to 21:01:00 IST (Queue is open, admission not yet started)
        testClock.setDateTime(LocalDateTime.of(SESSION_DATE.minusDays(1), LocalTime.of(21, 1, 0)));

        QueueEntryResponseDto joinResponse = bookingQueueService.joinQueue(
                new JoinQueueRequestDto(aliceHost.getId(), SESSION_DATE, SessionType.MORNING)
        );
        assertThat(joinResponse.status()).isEqualTo(QueueEntryStatus.WAITING);
        assertThat(joinResponse.positionInLine()).isEqualTo(1L);

        // Attempting to admit before 21:05 IST must state booking has not started
        AdmitNextResponseDto earlyAdmit = bookingQueueService.admitNextStudent(SESSION_DATE, SessionType.MORNING);
        assertThat(earlyAdmit.studentAdmitted()).isFalse();
        assertThat(earlyAdmit.message()).contains("Booking has not started yet");

        // --- STEP 3: Queue Admission ---
        // Advance clock to 21:05:01 IST
        testClock.setDateTime(LocalDateTime.of(SESSION_DATE.minusDays(1), LocalTime.of(21, 5, 1)));

        AdmitNextResponseDto admitResponse = bookingQueueService.admitNextStudent(SESSION_DATE, SessionType.MORNING);
        assertThat(admitResponse.studentAdmitted()).isTrue();
        assertThat(admitResponse.studentId()).isEqualTo(aliceHost.getId());
        assertThat(admitResponse.remainingSeconds()).isGreaterThan(0);

        // --- STEP 4: Atomic Group Booking Confirmation ---
        // Advance clock by 20 seconds (21:05:21 IST, well within 60-second window)
        testClock.advance(Duration.ofSeconds(20));

        CreateBookingRequestDto bookingRequest = CreateBookingRequestDto.of(
                aliceHost.getId(),
                morningSlot.getId(),
                List.of(aliceHost.getId(), bobMember.getId(), charlieMember.getId()),
                "E2E-IDEMPOTENCY-KEY-001"
        );

        BookingResponseDto bookingResponse = bookingService.confirmBooking(bookingRequest);
        assertThat(bookingResponse).isNotNull();
        assertThat(bookingResponse.courtNumber()).isEqualTo(1);
        assertThat(bookingResponse.players()).hasSize(3);

        // Verify Slot is BOOKED
        TimeSlot refreshedSlot = timeSlotRepository.findById(morningSlot.getId()).orElseThrow();
        assertThat(refreshedSlot.getStatus()).isEqualTo(SlotStatus.BOOKED);

        // Verify QueueEntry is marked COMPLETED
        QueueEntry refreshedQueueEntry = queueEntryRepository.findById(joinResponse.queueEntryId()).orElseThrow();
        assertThat(refreshedQueueEntry.getStatus()).isEqualTo(QueueEntryStatus.COMPLETED);

        // Verify Atomic Credit Deduction: Alice 6 -> 5, Bob 6 -> 5, Charlie 6 -> 5
        Student refreshedAlice = studentRepository.findById(aliceHost.getId()).orElseThrow();
        Student refreshedBob = studentRepository.findById(bobMember.getId()).orElseThrow();
        Student refreshedCharlie = studentRepository.findById(charlieMember.getId()).orElseThrow();
        assertThat(refreshedAlice.getWeeklyCredits()).isEqualTo(5);
        assertThat(refreshedBob.getWeeklyCredits()).isEqualTo(5);
        assertThat(refreshedCharlie.getWeeklyCredits()).isEqualTo(5);

        // --- STEP 5: Individual QR Passes Verification ---
        List<BookingPlayer> playersInDb = bookingPlayerRepository.findByBookingId(bookingResponse.bookingId());
        assertThat(playersInDb).hasSize(3);

        BookingPlayer alicePass = playersInDb.stream().filter(p -> p.getStudent().getId().equals(aliceHost.getId())).findFirst().orElseThrow();
        BookingPlayer bobPass = playersInDb.stream().filter(p -> p.getStudent().getId().equals(bobMember.getId())).findFirst().orElseThrow();
        BookingPlayer charliePass = playersInDb.stream().filter(p -> p.getStudent().getId().equals(charlieMember.getId())).findFirst().orElseThrow();

        assertThat(alicePass.getQrToken()).isNotBlank();
        assertThat(bobPass.getQrToken()).isNotBlank();
        assertThat(charliePass.getQrToken()).isNotBlank();

        // Ensure QR tokens are distinct and unpredictable
        assertThat(alicePass.getQrToken()).isNotEqualTo(bobPass.getQrToken());
        assertThat(bobPass.getQrToken()).isNotEqualTo(charliePass.getQrToken());

        // --- STEP 6: Idempotent Booking Submission ---
        BookingResponseDto duplicateSubmission = bookingService.confirmBooking(bookingRequest);
        assertThat(duplicateSubmission.bookingId()).isEqualTo(bookingResponse.bookingId());
        // Verify credits were NOT deducted twice
        assertThat(studentRepository.findById(aliceHost.getId()).orElseThrow().getWeeklyCredits()).isEqualTo(5);

        // --- STEP 7: Staff QR Check-In ---
        // Fast forward clock to game morning: 2026-10-15 06:05:00 IST (5 minutes into 06:00-06:30 slot, within 10m grace period)
        testClock.setDateTime(LocalDateTime.of(SESSION_DATE, LocalTime.of(6, 5, 0)));

        // Alice presents QR pass at check-in desk
        CheckInRequestDto checkInAlice = new CheckInRequestDto(alicePass.getQrToken(), "Staff Sam");
        CheckInResponseDto checkInResponse = checkInService.checkInPlayer(checkInAlice, "Staff Sam");

        assertThat(checkInResponse.success()).isTrue();
        assertThat(checkInResponse.alreadyCheckedIn()).isFalse();
        assertThat(checkInResponse.studentName()).isEqualTo("Alice Host");
        assertThat(checkInResponse.checkedInBy()).isEqualTo("Staff Sam");

        BookingPlayer aliceCheckedIn = bookingPlayerRepository.findById(alicePass.getId()).orElseThrow();
        assertThat(aliceCheckedIn.getCheckedIn()).isTrue();
        assertThat(aliceCheckedIn.getCheckedInBy()).isEqualTo("Staff Sam");

        // Duplicate scan check: scanning Alice's QR code again returns alreadyCheckedIn = true without altering state
        CheckInResponseDto duplicateScan = checkInService.checkInPlayer(checkInAlice, "Staff Sam");
        assertThat(duplicateScan.success()).isTrue();
        assertThat(duplicateScan.alreadyCheckedIn()).isTrue();
        assertThat(duplicateScan.message()).contains("already been checked in");

        // --- STEP 8: No-Show Processing ---
        // Fast forward clock past grace period: 2026-10-15 06:15:00 IST (15 minutes after 06:00 start; cutoff is 06:05)
        testClock.setDateTime(LocalDateTime.of(SESSION_DATE, LocalTime.of(6, 15, 0)));

        NoShowProcessingResponseDto noShowResponse = checkInService.processNoShows(morningSlot.getId(), "Staff Sam");
        assertThat(noShowResponse.penalizedPlayers()).hasSize(2); // Bob and Charlie
        assertThat(noShowResponse.processedCount()).isEqualTo(2);

        // Verify Alice (Checked in) was NOT penalized
        Student finalAlice = studentRepository.findById(aliceHost.getId()).orElseThrow();
        assertThat(finalAlice.getWeeklyCredits()).isEqualTo(5);
        assertThat(finalAlice.getReliabilityRating()).isEqualTo(100.0);

        // Verify Bob & Charlie (No-shows) were penalized: remaining credits forfeit to 0, rating reduced by 10 (100 -> 90)
        Student finalBob = studentRepository.findById(bobMember.getId()).orElseThrow();
        Student finalCharlie = studentRepository.findById(charlieMember.getId()).orElseThrow();
        assertThat(finalBob.getWeeklyCredits()).isEqualTo(0);
        assertThat(finalBob.getReliabilityRating()).isEqualTo(90.0);
        assertThat(finalCharlie.getWeeklyCredits()).isEqualTo(0);
        assertThat(finalCharlie.getReliabilityRating()).isEqualTo(90.0);

        // Verify repeated no-show processing cannot apply penalties twice (idempotent)
        NoShowProcessingResponseDto repeatNoShow = checkInService.processNoShows(morningSlot.getId(), "Staff Sam");
        assertThat(repeatNoShow.penalizedPlayers()).isEmpty();
        assertThat(finalBob.getWeeklyCredits()).isEqualTo(0);
    }

    @Test
    @DisplayName("Server strictly enforces 60-second admission window deadline and rejects late booking")
    void shouldEnforceSixtySecondAdmissionDeadlineStrictly() {
        // Open queue at 21:01 IST
        testClock.setDateTime(LocalDateTime.of(SESSION_DATE.minusDays(1), LocalTime.of(21, 1, 0)));
        bookingQueueService.joinQueue(new JoinQueueRequestDto(aliceHost.getId(), SESSION_DATE, SessionType.MORNING));

        // Admit student at 21:05:00 IST
        testClock.setDateTime(LocalDateTime.of(SESSION_DATE.minusDays(1), LocalTime.of(21, 5, 0)));
        bookingQueueService.admitNextStudent(SESSION_DATE, SessionType.MORNING);

        // Advance clock by 65 seconds (21:06:05 IST -> expired deadline)
        testClock.advance(Duration.ofSeconds(65));

        CreateBookingRequestDto lateRequest = CreateBookingRequestDto.of(
                aliceHost.getId(),
                morningSlot.getId(),
                List.of(aliceHost.getId(), bobMember.getId()),
                "LATE-BOOKING-001"
        );

        assertThatThrownBy(() -> bookingService.confirmBooking(lateRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Booking opportunity has expired");

        // Verify slot remains available and no credits were deducted
        TimeSlot slot = timeSlotRepository.findById(morningSlot.getId()).orElseThrow();
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(studentRepository.findById(aliceHost.getId()).orElseThrow().getWeeklyCredits()).isEqualTo(6);
    }

    @Test
    @DisplayName("Booking rolls back completely if any group teammate has zero credits")
    void shouldRejectBookingWhenTeammateHasZeroCreditsAndRollbackAll() {
        testClock.setDateTime(LocalDateTime.of(SESSION_DATE.minusDays(1), LocalTime.of(21, 1, 0)));
        bookingQueueService.joinQueue(new JoinQueueRequestDto(aliceHost.getId(), SESSION_DATE, SessionType.MORNING));

        testClock.setDateTime(LocalDateTime.of(SESSION_DATE.minusDays(1), LocalTime.of(21, 5, 0)));
        bookingQueueService.admitNextStudent(SESSION_DATE, SessionType.MORNING);

        // Group includes Dana (0 credits)
        CreateBookingRequestDto zeroCreditGroup = CreateBookingRequestDto.of(
                aliceHost.getId(),
                morningSlot.getId(),
                List.of(aliceHost.getId(), bobMember.getId(), danaZeroCredit.getId()),
                "ZERO-CREDIT-GROUP-001"
        );

        assertThatThrownBy(() -> bookingService.confirmBooking(zeroCreditGroup))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("insufficient weekly credits");

        // Verify rollback: Alice and Bob still have 6 credits
        assertThat(studentRepository.findById(aliceHost.getId()).orElseThrow().getWeeklyCredits()).isEqualTo(6);
        assertThat(studentRepository.findById(bobMember.getId()).orElseThrow().getWeeklyCredits()).isEqualTo(6);

        // Slot remains available
        assertThat(timeSlotRepository.findById(morningSlot.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(bookingRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Staff check-in rejects invalid, unknown, or expired QR tokens")
    void shouldRejectInvalidOrExpiredCheckInTokens() {
        testClock.setDateTime(LocalDateTime.of(SESSION_DATE, LocalTime.of(6, 5, 0)));

        // Unknown token
        assertThatThrownBy(() -> checkInService.checkInPlayer(new CheckInRequestDto("UNKNOWN_QR_9999", "Staff Sam"), "Staff Sam"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Invalid or unknown QR check-in token");

        // Blank token
        assertThatThrownBy(() -> checkInService.checkInPlayer(new CheckInRequestDto("", "Staff Sam"), "Staff Sam"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or blank");
    }

    /**
     * Controllable test clock for deterministic time manipulation in tests.
     */
    static class MutableTestClock extends Clock {
        private final AtomicReference<Instant> currentInstant;
        private final ZoneId zone;

        public MutableTestClock(Instant initialInstant, ZoneId zone) {
            this(new AtomicReference<>(initialInstant), zone);
        }

        private MutableTestClock(AtomicReference<Instant> currentInstant, ZoneId zone) {
            this.currentInstant = currentInstant;
            this.zone = zone;
        }

        public void setDateTime(LocalDateTime ldt) {
            this.currentInstant.set(ldt.atZone(zone).toInstant());
        }

        public void advance(Duration duration) {
            this.currentInstant.updateAndGet(i -> i.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId newZone) {
            return new MutableTestClock(this.currentInstant, newZone);
        }

        @Override
        public Instant instant() {
            return currentInstant.get();
        }
    }
}
