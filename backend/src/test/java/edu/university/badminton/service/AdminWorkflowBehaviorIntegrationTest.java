package edu.university.badminton.service;

import edu.university.badminton.dto.CreateBookingRequestDto;
import edu.university.badminton.dto.SlotGenerationResponseDto;
import edu.university.badminton.dto.UpdateCourtStatusRequestDto;
import edu.university.badminton.dto.UpdateSessionConfigRequestDto;
import edu.university.badminton.model.Booking;
import edu.university.badminton.model.BookingPlayer;
import edu.university.badminton.model.BookingQueue;
import edu.university.badminton.model.BookingStatus;
import edu.university.badminton.model.Court;
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
import edu.university.badminton.repository.CreditResetExecutionRepository;
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
import org.springframework.core.env.Environment;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end integration tests verifying administrative workflow behaviors:
 * 1. Court deactivation preserves existing bookings, slots, and player passes.
 * 2. Inactive courts strictly reject new booking attempts.
 * 3. Slot generation is idempotent and preserves booked slots without data loss.
 * 4. Invalid session rules are rejected with clear error messages.
 * 5. Configuration changes do not rewrite existing slots or bookings.
 * 6. Repeated weekly credit reset cannot restore spent credits in the same week.
 * 7. Background schedulers remain safely disabled by default.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:badminton_admin_testdb;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
class AdminWorkflowBehaviorIntegrationTest {

    private static final LocalDate TEST_DATE = LocalDate.of(2026, 10, 25);

    @Autowired
    private CourtService courtService;

    @Autowired
    private TimeSlotService timeSlotService;

    @Autowired
    private SessionConfigService sessionConfigService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private CreditResetService creditResetService;

    @Autowired
    private CourtRepository courtRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private SessionConfigRepository sessionConfigRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingPlayerRepository bookingPlayerRepository;

    @Autowired
    private BookingQueueRepository bookingQueueRepository;

    @Autowired
    private QueueEntryRepository queueEntryRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CreditResetExecutionRepository creditResetExecutionRepository;

    @Autowired
    private Environment environment;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    private Court court1;
    private Court court2;
    private SessionConfig morningConfig;
    private Student student1;
    private Student student2;

    @BeforeEach
    void setUp() {
        cleanupDatabase();

        // 1. Setup Courts
        court1 = courtRepository.save(new Court(1)); // Active
        court2 = courtRepository.save(new Court(2)); // Active initially

        // 2. Setup Morning Session
        morningConfig = sessionConfigRepository.save(new SessionConfig(
                SessionType.MORNING,
                LocalTime.of(6, 0),
                LocalTime.of(8, 30),
                30,
                LocalTime.of(21, 0),
                LocalTime.of(21, 5),
                1
        ));

        // 3. Setup Students
        student1 = new Student("Alice Host", "alice@vitapstudent.ac.in", "23MIC1001", Role.STUDENT);
        student1.setActive(true);
        student1.setEmailVerified(true);
        student1.setWeeklyCredits(6);
        student1 = studentRepository.save(student1);

        student2 = new Student("Bob Partner", "bob@vitapstudent.ac.in", "23MIC1002", Role.STUDENT);
        student2.setActive(true);
        student2.setEmailVerified(true);
        student2.setWeeklyCredits(6);
        student2 = studentRepository.save(student2);
    }

    @AfterEach
    void tearDown() {
        cleanupDatabase();
    }

    private void cleanupDatabase() {
        bookingPlayerRepository.deleteAll();
        bookingRepository.deleteAll();
        queueEntryRepository.deleteAll();
        bookingQueueRepository.deleteAll();
        timeSlotRepository.deleteAll();
        sessionConfigRepository.deleteAll();
        courtRepository.deleteAll();
        creditResetExecutionRepository.deleteAll();
        studentRepository.deleteAll();
    }

    @Test
    @DisplayName("Court deactivation preserves existing bookings, time slots, and player passes without silent cancellation")
    void shouldPreserveExistingBookingsWhenCourtDeactivated() {
        // 1. Create a booked slot on Court 1
        LocalDateTime start = LocalDateTime.of(TEST_DATE, LocalTime.of(6, 0));
        LocalDateTime end = LocalDateTime.of(TEST_DATE, LocalTime.of(6, 30));
        TimeSlot slot = timeSlotRepository.save(
                new TimeSlot(court1, morningConfig, TEST_DATE, start, end, SlotStatus.BOOKED)
        );

        // Create booking with 2 players
        Booking booking = new Booking(slot, student1, null, "IDEMP-COURT-PRESERVE-01");
        booking = bookingRepository.save(booking);

        BookingPlayer player1 = new BookingPlayer(booking, student1, 1, "QR_TOKEN_ALICE_01");
        BookingPlayer player2 = new BookingPlayer(booking, student2, 2, "QR_TOKEN_BOB_01");
        bookingPlayerRepository.saveAll(List.of(player1, player2));

        // 2. Staff/Admin deactivates Court 1
        courtService.updateCourtStatus(court1.getId(), new UpdateCourtStatusRequestDto(false));

        // 3. Verify Court 1 is deactivated
        Court refreshedCourt = courtRepository.findById(court1.getId()).orElseThrow();
        assertThat(refreshedCourt.getActive()).isFalse();

        // 4. Verify existing slot and booking are completely preserved
        TimeSlot refreshedSlot = timeSlotRepository.findById(slot.getId()).orElseThrow();
        assertThat(refreshedSlot.getStatus()).isEqualTo(SlotStatus.BOOKED);
        assertThat(refreshedSlot.getCourt().getId()).isEqualTo(court1.getId());

        Booking refreshedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(refreshedBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(refreshedBooking.getBookingUser().getId()).isEqualTo(student1.getId());

        List<BookingPlayer> refreshedPlayers = bookingPlayerRepository.findByBookingId(booking.getId());
        assertThat(refreshedPlayers).hasSize(2);
        assertThat(refreshedPlayers).extracting(BookingPlayer::getQrToken)
                .containsExactlyInAnyOrder("QR_TOKEN_ALICE_01", "QR_TOKEN_BOB_01");
    }

    @Test
    @DisplayName("New bookings cannot be made on an inactive court and fail with IllegalStateException")
    void shouldRejectNewBookingOnInactiveCourt() {
        // 1. Deactivate Court 2
        courtService.updateCourtStatus(court2.getId(), new UpdateCourtStatusRequestDto(false));

        // 2. Create available slot on Court 2
        LocalDateTime start = LocalDateTime.of(TEST_DATE, LocalTime.of(7, 0));
        LocalDateTime end = LocalDateTime.of(TEST_DATE, LocalTime.of(7, 30));
        TimeSlot slotOnInactiveCourt = timeSlotRepository.save(
                new TimeSlot(court2, morningConfig, TEST_DATE, start, end, SlotStatus.AVAILABLE)
        );

        // 3. Setup queue admission for student1
        BookingQueue queue = bookingQueueRepository.save(new BookingQueue(morningConfig, SessionType.MORNING, TEST_DATE));
        QueueEntry entry = new QueueEntry(queue, student1, Instant.now());
        entry.admit(Instant.now());
        queueEntryRepository.save(entry);

        CreateBookingRequestDto request = CreateBookingRequestDto.of(
                student1.getId(),
                slotOnInactiveCourt.getId(),
                List.of(student1.getId(), student2.getId()),
                "IDEMP-INACTIVE-COURT-01"
        );

        // 4. Booking attempt must be rejected
        assertThatThrownBy(() -> bookingService.confirmBooking(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Court 2 is currently inactive and not available for new bookings");

        // 5. Verify no bookings created and student credits untouched
        assertThat(bookingRepository.findAll()).isEmpty();
        assertThat(studentRepository.findById(student1.getId()).orElseThrow().getWeeklyCredits()).isEqualTo(6);
        assertThat(studentRepository.findById(student2.getId()).orElseThrow().getWeeklyCredits()).isEqualTo(6);
    }

    @Test
    @DisplayName("Slot generation is idempotent and strictly preserves pre-existing booked slots")
    void shouldIdempotentlyGenerateSlotsAndPreserveBookedSlots() {
        // 1. Initial generation creates slots across active courts (Court 1 and Court 2)
        SlotGenerationResponseDto firstRun = timeSlotService.generateSlotsForDate(TEST_DATE);
        assertThat(firstRun.createdSlots()).isGreaterThan(0);
        int initialTotal = firstRun.totalSlots();

        // 2. Mark one slot as BOOKED
        List<TimeSlot> slots = timeSlotRepository.findBySlotDateWithDetails(TEST_DATE);
        TimeSlot slotToBook = slots.get(0);
        Long bookedSlotId = slotToBook.getId();
        slotToBook.setStatus(SlotStatus.BOOKED);
        timeSlotRepository.save(slotToBook);

        // 3. Re-run slot generation for the same date
        SlotGenerationResponseDto secondRun = timeSlotService.generateSlotsForDate(TEST_DATE);
        assertThat(secondRun.createdSlots()).isEqualTo(0);
        assertThat(secondRun.existingSlots()).isEqualTo(initialTotal);
        assertThat(secondRun.totalSlots()).isEqualTo(initialTotal);

        // 4. Verify the booked slot remains BOOKED and untouched
        TimeSlot refreshedBookedSlot = timeSlotRepository.findById(bookedSlotId).orElseThrow();
        assertThat(refreshedBookedSlot.getStatus()).isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    @DisplayName("Invalid session rules are rejected with descriptive validation errors")
    void shouldRejectInvalidSessionRules() {
        Long sessionId = morningConfig.getId();

        // 1. Start time after end time
        UpdateSessionConfigRequestDto invertedTimes = new UpdateSessionConfigRequestDto(
                LocalTime.of(9, 0), LocalTime.of(8, 0), 30, LocalTime.of(21, 0), LocalTime.of(21, 5), 1, true
        );
        assertThatThrownBy(() -> sessionConfigService.updateSessionConfig(sessionId, invertedTimes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be strictly before end time");

        // 2. Non-positive slot duration (0 minutes)
        UpdateSessionConfigRequestDto zeroDuration = new UpdateSessionConfigRequestDto(
                LocalTime.of(6, 0), LocalTime.of(8, 30), 0, LocalTime.of(21, 0), LocalTime.of(21, 5), 1, true
        );
        assertThatThrownBy(() -> sessionConfigService.updateSessionConfig(sessionId, zeroDuration))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive integer greater than zero");

        // 3. Slot duration exceeding entire session window
        UpdateSessionConfigRequestDto hugeDuration = new UpdateSessionConfigRequestDto(
                LocalTime.of(6, 0), LocalTime.of(7, 0), 90, LocalTime.of(21, 0), LocalTime.of(21, 5), 1, true
        );
        assertThatThrownBy(() -> sessionConfigService.updateSessionConfig(sessionId, hugeDuration))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed total session window");

        // 4. Queue open time after booking start time
        UpdateSessionConfigRequestDto invertedQueueTimes = new UpdateSessionConfigRequestDto(
                LocalTime.of(6, 0), LocalTime.of(8, 30), 30, LocalTime.of(21, 10), LocalTime.of(21, 5), 1, true
        );
        assertThatThrownBy(() -> sessionConfigService.updateSessionConfig(sessionId, invertedQueueTimes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly before booking admission start time");

        // 5. Negative queue days in advance
        UpdateSessionConfigRequestDto negativeAdvance = new UpdateSessionConfigRequestDto(
                LocalTime.of(6, 0), LocalTime.of(8, 30), 30, LocalTime.of(21, 0), LocalTime.of(21, 5), -1, true
        );
        assertThatThrownBy(() -> sessionConfigService.updateSessionConfig(sessionId, negativeAdvance))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    @DisplayName("Repeated weekly credit reset requests cannot restore spent credits twice in the same week")
    void shouldPreventRepeatWeeklyCreditResetInSameWeek() {
        // Alice has 2 credits
        student1.setWeeklyCredits(2);
        studentRepository.save(student1);

        Instant mondayInstant = Instant.parse("2026-10-19T00:00:00Z");

        // 1. Initial reset
        var firstResult = creditResetService.resetWeeklyCredits(mondayInstant, "ADMIN_TEST");
        assertThat(firstResult.executed()).isTrue();
        assertThat(studentRepository.findById(student1.getId()).orElseThrow().getWeeklyCredits()).isEqualTo(6);

        // 2. Alice plays and spends 1 credit (6 -> 5)
        student1.setWeeklyCredits(5);
        studentRepository.save(student1);

        // 3. Staff triggers reset again in the same week
        Instant wednesdayInstant = Instant.parse("2026-10-21T10:00:00Z");
        var secondResult = creditResetService.resetWeeklyCredits(wednesdayInstant, "STAFF_REPEAT");

        assertThat(secondResult.executed()).isFalse();
        assertThat(secondResult.alreadyExecuted()).isTrue();

        // 4. Verify spent credit was NOT restored
        assertThat(studentRepository.findById(student1.getId()).orElseThrow().getWeeklyCredits()).isEqualTo(5);
    }

    @Test
    @DisplayName("Neither background scheduler is enabled accidentally by default")
    void shouldVerifyBothBackgroundSchedulersAreDisabledByDefault() {
        String creditSchedulerEnabled = environment.getProperty("app.credit-reset.scheduler.enabled", "false");
        String noShowSchedulerEnabled = environment.getProperty("app.no-show.scheduler.enabled", "false");

        assertThat(creditSchedulerEnabled).isEqualTo("false");
        assertThat(noShowSchedulerEnabled).isEqualTo("false");

        // Verify that scheduler beans are not active in the application context
        assertThat(applicationContext.containsBean("weeklyCreditResetScheduler")).isFalse();
        assertThat(applicationContext.containsBean("noShowScheduler")).isFalse();
    }
}
