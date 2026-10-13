package edu.university.badminton.service;

import edu.university.badminton.dto.CheckInRequestDto;
import edu.university.badminton.dto.CheckInResponseDto;
import edu.university.badminton.dto.NoShowProcessingResponseDto;
import edu.university.badminton.model.Booking;
import edu.university.badminton.model.BookingPlayer;
import edu.university.badminton.model.BookingStatus;
import edu.university.badminton.model.Court;
import edu.university.badminton.model.Role;
import edu.university.badminton.model.SessionConfig;
import edu.university.badminton.model.SessionType;
import edu.university.badminton.model.Student;
import edu.university.badminton.model.TimeSlot;
import edu.university.badminton.repository.BookingPlayerRepository;
import edu.university.badminton.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckInServiceTest {

    private static final ZoneId TEST_ZONE = ZoneId.of("UTC");
    private static final LocalDate TEST_DATE = LocalDate.of(2026, 10, 15);
    private static final LocalDateTime SLOT_START = LocalDateTime.of(TEST_DATE, LocalTime.of(6, 0));
    private static final LocalDateTime SLOT_END = LocalDateTime.of(TEST_DATE, LocalTime.of(6, 30));

    @Mock
    private BookingPlayerRepository bookingPlayerRepository;

    @Mock
    private StudentRepository studentRepository;

    private CheckInService checkInService;

    private Student student1;
    private Court court1;
    private SessionConfig sessionConfig;
    private TimeSlot timeSlot;
    private Booking booking;
    private BookingPlayer player;

    @BeforeEach
    void setUp() {
        student1 = new Student("Hamsa Hrudai", "hamsa.23mic7333@vitapstudent.ac.in", "23MIC7333", Role.STUDENT);
        student1.setId(1L);
        student1.setWeeklyCredits(5);
        student1.setReliabilityRating(100.0);

        court1 = new Court(1);
        court1.setId(10L);

        sessionConfig = new SessionConfig(SessionType.MORNING, LocalTime.of(6, 0), LocalTime.of(8, 30), 30, LocalTime.of(21, 0), LocalTime.of(21, 5), 1);
        sessionConfig.setId(20L);

        timeSlot = new TimeSlot(court1, sessionConfig, TEST_DATE, SLOT_START, SLOT_END);
        timeSlot.setId(100L);

        booking = new Booking(timeSlot, student1, null, "IDEM_1");
        booking.setId(200L);
        booking.setStatus(BookingStatus.CONFIRMED);

        player = new BookingPlayer(booking, student1, 1, "QR_VALID_TOKEN_123");
        player.setId(300L);
        player.setCheckedIn(false);
        player.setNoShow(false);
    }

    private void initServiceWithClock(LocalDateTime currentTime) {
        Instant currentInstant = currentTime.atZone(TEST_ZONE).toInstant();
        Clock clock = Clock.fixed(currentInstant, TEST_ZONE);
        checkInService = new CheckInService(bookingPlayerRepository, studentRepository, clock);
    }

    @Test
    @DisplayName("Successfully checks in player within the valid grace period window")
    void shouldCheckInSuccessfullyWithinWindow() {
        // Slot is 06:00. Check-in at 06:05 (5 minutes after start)
        initServiceWithClock(LocalDateTime.of(TEST_DATE, LocalTime.of(6, 5)));

        when(bookingPlayerRepository.findByQrTokenWithDetails("QR_VALID_TOKEN_123"))
                .thenReturn(Optional.of(player));
        when(bookingPlayerRepository.save(any(BookingPlayer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CheckInRequestDto request = new CheckInRequestDto("QR_VALID_TOKEN_123", "STAFF_BOB");
        CheckInResponseDto response = checkInService.checkInPlayer(request);

        assertThat(response.success()).isTrue();
        assertThat(response.alreadyCheckedIn()).isFalse();
        assertThat(response.studentName()).isEqualTo("Hamsa Hrudai");
        assertThat(response.checkedInBy()).isEqualTo("STAFF_BOB");
        assertThat(response.checkedInAt()).isNotNull();

        assertThat(player.getCheckedIn()).isTrue();
        assertThat(player.getCheckedInBy()).isEqualTo("STAFF_BOB");
        verify(bookingPlayerRepository).save(player);
    }

    @Test
    @DisplayName("Successfully checks in player 10 minutes before slot start (window opens at -15m)")
    void shouldCheckInSuccessfullyBeforeSlotStart() {
        // Slot is 06:00. Check-in at 05:50 (10 minutes before start)
        initServiceWithClock(LocalDateTime.of(TEST_DATE, LocalTime.of(5, 50)));

        when(bookingPlayerRepository.findByQrTokenWithDetails("QR_VALID_TOKEN_123"))
                .thenReturn(Optional.of(player));
        when(bookingPlayerRepository.save(any(BookingPlayer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CheckInRequestDto request = new CheckInRequestDto("QR_VALID_TOKEN_123", null);
        CheckInResponseDto response = checkInService.checkInPlayer(request);

        assertThat(response.success()).isTrue();
        assertThat(response.alreadyCheckedIn()).isFalse();
        assertThat(response.checkedInBy()).isEqualTo("FACILITY_STAFF");
        assertThat(player.getCheckedIn()).isTrue();
    }

    @Test
    @DisplayName("Idempotent check-in: Scanning an already checked-in player returns success without modifying state")
    void shouldHandleDuplicateScanIdempotently() {
        Instant originalCheckInAt = SLOT_START.atZone(TEST_ZONE).toInstant();
        player.setCheckedIn(true);
        player.setCheckedInAt(originalCheckInAt);
        player.setCheckedInBy("STAFF_ALICE");

        initServiceWithClock(LocalDateTime.of(TEST_DATE, LocalTime.of(6, 8)));

        when(bookingPlayerRepository.findByQrTokenWithDetails("QR_VALID_TOKEN_123"))
                .thenReturn(Optional.of(player));

        CheckInRequestDto request = new CheckInRequestDto("QR_VALID_TOKEN_123", "STAFF_BOB");
        CheckInResponseDto response = checkInService.checkInPlayer(request);

        assertThat(response.success()).isTrue();
        assertThat(response.alreadyCheckedIn()).isTrue();
        assertThat(response.message()).contains("already been checked in");
        assertThat(response.checkedInAt()).isEqualTo(originalCheckInAt);
        assertThat(response.checkedInBy()).isEqualTo("STAFF_ALICE");

        // Should not re-save or overwrite original attributes
        verify(bookingPlayerRepository, never()).save(any(BookingPlayer.class));
    }

    @Test
    @DisplayName("Rejects check-in when QR token is unknown")
    void shouldThrowWhenQrTokenNotFound() {
        initServiceWithClock(LocalDateTime.of(TEST_DATE, LocalTime.of(6, 0)));

        when(bookingPlayerRepository.findByQrTokenWithDetails("UNKNOWN_TOKEN"))
                .thenReturn(Optional.empty());

        CheckInRequestDto request = new CheckInRequestDto("UNKNOWN_TOKEN", "STAFF_1");

        assertThatThrownBy(() -> checkInService.checkInPlayer(request))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Invalid or unknown QR check-in token");
    }

    @Test
    @DisplayName("Rejects check-in before check-in window opens (more than 15 minutes prior)")
    void shouldRejectWhenTooEarly() {
        // Slot is 06:00. Attempt check-in at 05:40 (20 minutes before start)
        initServiceWithClock(LocalDateTime.of(TEST_DATE, LocalTime.of(5, 40)));

        when(bookingPlayerRepository.findByQrTokenWithDetails("QR_VALID_TOKEN_123"))
                .thenReturn(Optional.of(player));

        CheckInRequestDto request = new CheckInRequestDto("QR_VALID_TOKEN_123", "STAFF_1");

        assertThatThrownBy(() -> checkInService.checkInPlayer(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Check-in window is not yet open");
    }

    @Test
    @DisplayName("Rejects check-in after the 10-minute grace period has expired")
    void shouldRejectWhenGracePeriodExpired() {
        // Slot is 06:00. Attempt check-in at 06:11 (11 minutes after start)
        initServiceWithClock(LocalDateTime.of(TEST_DATE, LocalTime.of(6, 11)));

        when(bookingPlayerRepository.findByQrTokenWithDetails("QR_VALID_TOKEN_123"))
                .thenReturn(Optional.of(player));

        CheckInRequestDto request = new CheckInRequestDto("QR_VALID_TOKEN_123", "STAFF_1");

        assertThatThrownBy(() -> checkInService.checkInPlayer(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Check-in grace period expired");
    }

    @Test
    @DisplayName("Rejects check-in for a cancelled booking")
    void shouldRejectWhenBookingIsCancelled() {
        booking.setStatus(BookingStatus.CANCELLED);
        initServiceWithClock(LocalDateTime.of(TEST_DATE, LocalTime.of(6, 5)));

        when(bookingPlayerRepository.findByQrTokenWithDetails("QR_VALID_TOKEN_123"))
                .thenReturn(Optional.of(player));

        CheckInRequestDto request = new CheckInRequestDto("QR_VALID_TOKEN_123", "STAFF_1");

        assertThatThrownBy(() -> checkInService.checkInPlayer(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot check in player for a cancelled booking");
    }

    @Test
    @DisplayName("Rejects check-in if player has already been processed as a no-show")
    void shouldRejectWhenPlayerMarkedNoShow() {
        player.setNoShow(true);
        initServiceWithClock(LocalDateTime.of(TEST_DATE, LocalTime.of(6, 5)));

        when(bookingPlayerRepository.findByQrTokenWithDetails("QR_VALID_TOKEN_123"))
                .thenReturn(Optional.of(player));

        CheckInRequestDto request = new CheckInRequestDto("QR_VALID_TOKEN_123", "STAFF_1");

        assertThatThrownBy(() -> checkInService.checkInPlayer(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Player is marked as a no-show");
    }

    @Test
    @DisplayName("No-show processing: forfeits remaining credits to 0 and reduces reliability rating by 10")
    void shouldProcessNoShowsAndApplyPenalties() {
        // Slot is 06:00. Grace period expires at 06:10.
        // Current time is 06:15 -> cutoff is 06:05
        LocalDateTime now = LocalDateTime.of(TEST_DATE, LocalTime.of(6, 15));
        LocalDateTime cutoff = now.minusMinutes(10);
        initServiceWithClock(now);

        Student student2 = new Student("Bomba", "bomba@vitapstudent.ac.in", "23MIC9999", Role.STUDENT);
        student2.setId(2L);
        student2.setWeeklyCredits(4);
        student2.setReliabilityRating(95.0);

        BookingPlayer player2 = new BookingPlayer(booking, student2, 2, "QR_TOKEN_BOMBA");
        player2.setId(301L);
        player2.setCheckedIn(false);
        player2.setNoShow(false);

        when(bookingPlayerRepository.findPendingNoShowIds(BookingStatus.CONFIRMED, cutoff))
                .thenReturn(List.of(301L));
        when(bookingPlayerRepository.findByIdWithDetailsAndLock(301L))
                .thenReturn(Optional.of(player2));

        NoShowProcessingResponseDto result = checkInService.processNoShows(null);

        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.penalizedPlayers()).hasSize(1);
        assertThat(result.penalizedPlayers().get(0).studentName()).isEqualTo("Bomba");
        assertThat(result.penalizedPlayers().get(0).forfeitedCredits()).isEqualTo(4);
        assertThat(result.penalizedPlayers().get(0).updatedReliabilityRating()).isEqualTo(85.0);

        // Verify entity updates
        assertThat(player2.getNoShow()).isTrue();
        assertThat(player2.getNoShowProcessedAt()).isNotNull();
        assertThat(student2.getWeeklyCredits()).isEqualTo(0);
        assertThat(student2.getReliabilityRating()).isEqualTo(85.0);

        verify(studentRepository).save(student2);
        verify(bookingPlayerRepository).save(player2);
    }

    @Test
    @DisplayName("No-show processing is idempotent: repeated runs find no pending players and do not re-penalize")
    void shouldBeIdempotentOnRepeatedNoShowRuns() {
        LocalDateTime now = LocalDateTime.of(TEST_DATE, LocalTime.of(6, 20));
        LocalDateTime cutoff = now.minusMinutes(10);
        initServiceWithClock(now);

        // First run returned no pending players
        when(bookingPlayerRepository.findPendingNoShowIds(BookingStatus.CONFIRMED, cutoff))
                .thenReturn(List.of());

        NoShowProcessingResponseDto result = checkInService.processNoShows(null);

        assertThat(result.processedCount()).isEqualTo(0);
        assertThat(result.penalizedPlayers()).isEmpty();
        verify(studentRepository, never()).save(any(Student.class));
        verify(bookingPlayerRepository, never()).save(any(BookingPlayer.class));
    }

    @Test
    @DisplayName("Check-in is permitted at the exact 10-minute boundary (06:10:00 for 06:00:00 slot)")
    void shouldPermitCheckInAtExactTenMinuteBoundary() {
        initServiceWithClock(LocalDateTime.of(TEST_DATE, LocalTime.of(6, 10, 0)));

        when(bookingPlayerRepository.findByQrTokenWithDetails("QR_VALID_TOKEN_123"))
                .thenReturn(Optional.of(player));
        when(bookingPlayerRepository.save(any(BookingPlayer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CheckInRequestDto request = new CheckInRequestDto("QR_VALID_TOKEN_123", null);
        CheckInResponseDto response = checkInService.checkInPlayer(request, "STAFF_AUTHORIZED");

        assertThat(response.success()).isTrue();
        assertThat(response.checkedInBy()).isEqualTo("STAFF_AUTHORIZED");
        assertThat(player.getCheckedIn()).isTrue();
    }

    @Test
    @DisplayName("Check-in is rejected 1 second past the 10-minute boundary (06:10:01 for 06:00:00 slot)")
    void shouldRejectCheckInOneSecondPastTenMinuteBoundary() {
        initServiceWithClock(LocalDateTime.of(TEST_DATE, LocalTime.of(6, 10, 1)));

        when(bookingPlayerRepository.findByQrTokenWithDetails("QR_VALID_TOKEN_123"))
                .thenReturn(Optional.of(player));

        CheckInRequestDto request = new CheckInRequestDto("QR_VALID_TOKEN_123", null);

        assertThatThrownBy(() -> checkInService.checkInPlayer(request, "STAFF_AUTHORIZED"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Check-in grace period expired");
    }

    @Test
    @DisplayName("Authenticated staff identity takes precedence over client-supplied staffIdentifier")
    void shouldPrioritizeAuthenticatedStaffOverClientSuppliedIdentifier() {
        initServiceWithClock(LocalDateTime.of(TEST_DATE, LocalTime.of(6, 5)));

        when(bookingPlayerRepository.findByQrTokenWithDetails("QR_VALID_TOKEN_123"))
                .thenReturn(Optional.of(player));
        when(bookingPlayerRepository.save(any(BookingPlayer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CheckInRequestDto request = new CheckInRequestDto("QR_VALID_TOKEN_123", "SPOOFED_STAFF_NAME");
        CheckInResponseDto response = checkInService.checkInPlayer(request, "VERIFIED_STAFF_MARSHAL");

        assertThat(response.checkedInBy()).isEqualTo("VERIFIED_STAFF_MARSHAL");
        assertThat(player.getCheckedInBy()).isEqualTo("VERIFIED_STAFF_MARSHAL");
    }

    @Test
    @DisplayName("No-show processing: checked-in player is never penalized")
    void shouldNeverPenalizeCheckedInPlayer() {
        LocalDateTime now = LocalDateTime.of(TEST_DATE, LocalTime.of(6, 15));
        initServiceWithClock(now);

        BookingPlayer checkedInPlayer = new BookingPlayer(booking, student1, 1, "QR_CHECKED_IN");
        checkedInPlayer.setId(401L);
        checkedInPlayer.setCheckedIn(true);
        checkedInPlayer.setNoShow(false);

        when(bookingPlayerRepository.findPendingNoShowIds(any(), any()))
                .thenReturn(List.of(401L));
        when(bookingPlayerRepository.findByIdWithDetailsAndLock(401L))
                .thenReturn(Optional.of(checkedInPlayer));

        NoShowProcessingResponseDto result = checkInService.processNoShows(null);

        assertThat(result.processedCount()).isEqualTo(0);
        assertThat(result.penalizedPlayers()).isEmpty();
        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    @DisplayName("No-show processing: cancelled booking is never penalized")
    void shouldNeverPenalizeCancelledBooking() {
        LocalDateTime now = LocalDateTime.of(TEST_DATE, LocalTime.of(6, 15));
        initServiceWithClock(now);

        Booking cancelledBooking = new Booking(timeSlot, student1, null, "idemp_cancel");
        cancelledBooking.setStatus(BookingStatus.CANCELLED);

        BookingPlayer cancelledPlayer = new BookingPlayer(cancelledBooking, student1, 1, "QR_CANCELLED");
        cancelledPlayer.setId(402L);
        cancelledPlayer.setCheckedIn(false);
        cancelledPlayer.setNoShow(false);

        when(bookingPlayerRepository.findPendingNoShowIds(any(), any()))
                .thenReturn(List.of(402L));
        when(bookingPlayerRepository.findByIdWithDetailsAndLock(402L))
                .thenReturn(Optional.of(cancelledPlayer));

        NoShowProcessingResponseDto result = checkInService.processNoShows(null);

        assertThat(result.processedCount()).isEqualTo(0);
        assertThat(result.penalizedPlayers()).isEmpty();
        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    @DisplayName("No-show processing: reliability rating never drops below 0.0")
    void shouldNeverReduceReliabilityBelowZero() {
        LocalDateTime now = LocalDateTime.of(TEST_DATE, LocalTime.of(6, 15));
        LocalDateTime cutoff = now.minusMinutes(10);
        initServiceWithClock(now);

        Student lowReliabilityStudent = new Student("Low Score", "low@vitapstudent.ac.in", "23LOW001", Role.STUDENT);
        lowReliabilityStudent.setId(501L);
        lowReliabilityStudent.setWeeklyCredits(2);
        lowReliabilityStudent.setReliabilityRating(5.0); // Less than 10 penalty

        BookingPlayer playerToPenalize = new BookingPlayer(booking, lowReliabilityStudent, 1, "QR_LOW_SCORE");
        playerToPenalize.setId(502L);
        playerToPenalize.setCheckedIn(false);
        playerToPenalize.setNoShow(false);

        when(bookingPlayerRepository.findPendingNoShowIds(BookingStatus.CONFIRMED, cutoff))
                .thenReturn(List.of(502L));
        when(bookingPlayerRepository.findByIdWithDetailsAndLock(502L))
                .thenReturn(Optional.of(playerToPenalize));
        when(studentRepository.findByIdWithLock(501L))
                .thenReturn(Optional.of(lowReliabilityStudent));

        NoShowProcessingResponseDto result = checkInService.processNoShows(null);

        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.penalizedPlayers().get(0).updatedReliabilityRating()).isEqualTo(0.0);
        assertThat(lowReliabilityStudent.getReliabilityRating()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Concurrency protection: skips player if marked as no-show by concurrent thread while waiting for lock")
    void shouldSkipPlayerIfMarkedNoShowByConcurrentThread() {
        LocalDateTime now = LocalDateTime.of(TEST_DATE, LocalTime.of(6, 15));
        LocalDateTime cutoff = now.minusMinutes(10);
        initServiceWithClock(now);

        // Candidate retrieved before lock acquisition
        BookingPlayer candidate = new BookingPlayer(booking, student1, 1, "QR_RACE");
        candidate.setId(601L);
        candidate.setCheckedIn(false);
        candidate.setNoShow(false);

        // But when locked, another thread already set noShow = true!
        BookingPlayer alreadyProcessedPlayer = new BookingPlayer(booking, student1, 1, "QR_RACE");
        alreadyProcessedPlayer.setId(601L);
        alreadyProcessedPlayer.setCheckedIn(false);
        alreadyProcessedPlayer.setNoShow(true);

        when(bookingPlayerRepository.findPendingNoShowIds(BookingStatus.CONFIRMED, cutoff))
                .thenReturn(List.of(601L));
        when(bookingPlayerRepository.findByIdWithDetailsAndLock(601L))
                .thenReturn(Optional.of(alreadyProcessedPlayer));

        NoShowProcessingResponseDto result = checkInService.processNoShows(null);

        assertThat(result.processedCount()).isEqualTo(0);
        assertThat(result.penalizedPlayers()).isEmpty();
        verify(studentRepository, never()).save(any(Student.class));
    }
}
