package edu.university.badminton.service;

import edu.university.badminton.dto.BookingPlayerAssignmentDto;
import edu.university.badminton.dto.BookingResponseDto;
import edu.university.badminton.dto.CreateBookingRequestDto;
import edu.university.badminton.model.Booking;
import edu.university.badminton.model.BookingPlayer;
import edu.university.badminton.model.BookingQueue;
import edu.university.badminton.model.BookingStatus;
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
import edu.university.badminton.repository.StudentRepository;
import edu.university.badminton.repository.TimeSlotRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingPlayerRepository bookingPlayerRepository;

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private BookingQueueRepository bookingQueueRepository;

    @Mock
    private QueueEntryRepository queueEntryRepository;

    @Mock
    private CourtRepository courtRepository;

    private Clock clock;
    private Instant fixedInstant;
    private BookingService bookingService;

    private Student student1;
    private Student student2;
    private Student student3;
    private Student student4;

    private Court court1;
    private SessionConfig morningConfig;
    private TimeSlot timeSlot;
    private BookingQueue queue;
    private QueueEntry queueEntry;

    @BeforeEach
    void setUp() {
        fixedInstant = Instant.parse("2026-10-14T21:05:30Z");
        clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));
        bookingService = new BookingService(
                bookingRepository,
                bookingPlayerRepository,
                timeSlotRepository,
                studentRepository,
                bookingQueueRepository,
                queueEntryRepository,
                courtRepository,
                clock
        );

        student1 = new Student("Alice Smith", "alice@university.edu", "2024BCS001", Role.STUDENT);
        student1.setId(1L);
        student1.setWeeklyCredits(6);
        student1.setActive(true);

        student2 = new Student("Bob Jones", "bob@university.edu", "2024BCS002", Role.STUDENT);
        student2.setId(2L);
        student2.setWeeklyCredits(4);
        student2.setActive(true);

        student3 = new Student("Charlie Brown", "charlie@university.edu", "2024BCS003", Role.STUDENT);
        student3.setId(3L);
        student3.setWeeklyCredits(2);
        student3.setActive(true);

        student4 = new Student("Diana Prince", "diana@university.edu", "2024BCS004", Role.STUDENT);
        student4.setId(4L);
        student4.setWeeklyCredits(5);
        student4.setActive(true);

        court1 = new Court(1);
        court1.setId(10L);

        morningConfig = new SessionConfig(SessionType.MORNING, LocalTime.of(6, 0), LocalTime.of(8, 30), 30, LocalTime.of(21, 0), LocalTime.of(21, 5), 1);
        morningConfig.setId(20L);

        LocalDate slotDate = LocalDate.of(2026, 10, 15);
        LocalDateTime startTime = LocalDateTime.of(slotDate, LocalTime.of(6, 0));
        LocalDateTime endTime = LocalDateTime.of(slotDate, LocalTime.of(6, 30));

        timeSlot = new TimeSlot(court1, morningConfig, slotDate, startTime, endTime, SlotStatus.AVAILABLE);
        timeSlot.setId(100L);

        queue = new BookingQueue(morningConfig, SessionType.MORNING, slotDate);
        queue.setId(200L);

        queueEntry = new QueueEntry(queue, student1, fixedInstant.minusSeconds(10));
        queueEntry.setId(300L);
        queueEntry.admit(fixedInstant.minusSeconds(10)); // expires at +50s from now

        lenient().when(courtRepository.findByIdWithLock(10L)).thenReturn(Optional.of(court1));
    }

    @Test
    @DisplayName("Should reject booking when target court is inactive")
    void shouldRejectBookingWhenTargetCourtIsInactive() {
        court1.setActive(false);
        when(timeSlotRepository.findByIdWithLock(100L)).thenReturn(Optional.of(timeSlot));
        when(courtRepository.findByIdWithLock(10L)).thenReturn(Optional.of(court1));

        CreateBookingRequestDto request = CreateBookingRequestDto.of(
                1L,
                100L,
                List.of(1L, 2L),
                null
        );

        assertThatThrownBy(() -> bookingService.confirmBooking(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Court 1 is currently inactive and not available for new bookings");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should successfully confirm booking and deduct 1 weekly credit from each group member")
    void shouldConfirmBookingAndDeductOneCreditPerMember() {
        CreateBookingRequestDto request = CreateBookingRequestDto.of(
                1L,
                100L,
                List.of(1L, 2L, 3L),
                "idemp-1"
        );

        when(bookingRepository.findByIdempotencyKey("idemp-1")).thenReturn(Optional.empty());
        when(timeSlotRepository.findByIdWithLock(100L)).thenReturn(Optional.of(timeSlot));
        when(bookingQueueRepository.findBySessionDateAndSessionType(LocalDate.of(2026, 10, 15), SessionType.MORNING))
                .thenReturn(Optional.of(queue));
        when(queueEntryRepository.findByQueueIdAndStudentId(200L, 1L))
                .thenReturn(Optional.of(queueEntry));

        when(studentRepository.findByIdWithLock(1L)).thenReturn(Optional.of(student1));
        when(studentRepository.findByIdWithLock(2L)).thenReturn(Optional.of(student2));
        when(studentRepository.findByIdWithLock(3L)).thenReturn(Optional.of(student3));

        when(bookingPlayerRepository.existsByStudentIdAndSlotStartTimeAndBookingStatus(any(), any(), any()))
                .thenReturn(false);

        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            b.setId(500L);
            return b;
        });

        BookingResponseDto response = bookingService.confirmBooking(request);

        assertThat(response).isNotNull();
        assertThat(response.bookingId()).isEqualTo(500L);
        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.creditsDeductedPerPlayer()).isEqualTo(1);
        assertThat(response.players()).hasSize(3);

        // Verify credit deductions: 1 credit deducted from each member
        assertThat(student1.getWeeklyCredits()).isEqualTo(5); // 6 - 1
        assertThat(student2.getWeeklyCredits()).isEqualTo(3); // 4 - 1
        assertThat(student3.getWeeklyCredits()).isEqualTo(1); // 2 - 1

        // Verify slot status changed to BOOKED
        assertThat(timeSlot.getStatus()).isEqualTo(SlotStatus.BOOKED);

        // Verify queue entry transitioned to COMPLETED
        assertThat(queueEntry.getStatus()).isEqualTo(QueueEntryStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should reject booking if group has fewer than 2 students")
    void shouldRejectGroupWithFewerThanTwoStudents() {
        CreateBookingRequestDto request = CreateBookingRequestDto.of(
                1L, 100L, List.of(1L), "idemp-too-small"
        );

        assertThatThrownBy(() -> bookingService.confirmBooking(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 2 and 4");
    }

    @Test
    @DisplayName("Should reject booking if group has more than 4 students")
    void shouldRejectGroupWithMoreThanFourStudents() {
        CreateBookingRequestDto request = CreateBookingRequestDto.of(
                1L, 100L, List.of(1L, 2L, 3L, 4L, 5L), "idemp-too-large"
        );

        assertThatThrownBy(() -> bookingService.confirmBooking(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 2 and 4");
    }

    @Test
    @DisplayName("Should reject booking if booking student is not in the group")
    void shouldRejectIfBookingStudentNotInGroup() {
        CreateBookingRequestDto request = CreateBookingRequestDto.of(
                1L, 100L, List.of(2L, 3L), "idemp-missing-user"
        );

        assertThatThrownBy(() -> bookingService.confirmBooking(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be included in the player group");
    }

    @Test
    @DisplayName("Should reject booking if duplicate students appear in the group")
    void shouldRejectDuplicateStudentsInGroup() {
        CreateBookingRequestDto request = CreateBookingRequestDto.of(
                1L, 100L, List.of(1L, 1L), "idemp-duplicate-student"
        );

        assertThatThrownBy(() -> bookingService.confirmBooking(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate student");
    }

    @Test
    @DisplayName("Should reject booking if time slot is already BOOKED")
    void shouldRejectIfTimeSlotAlreadyBooked() {
        timeSlot.setStatus(SlotStatus.BOOKED);
        CreateBookingRequestDto request = CreateBookingRequestDto.of(1L, 100L, List.of(1L, 2L), "idemp-booked");

        when(timeSlotRepository.findByIdWithLock(100L)).thenReturn(Optional.of(timeSlot));

        assertThatThrownBy(() -> bookingService.confirmBooking(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not available for booking");

        // Verify no credits were deducted
        assertThat(student1.getWeeklyCredits()).isEqualTo(6);
        assertThat(student2.getWeeklyCredits()).isEqualTo(4);
    }

    @Test
    @DisplayName("Should reject booking if student booking opportunity has expired")
    void shouldRejectIfOpportunityExpired() {
        queueEntry.setExpiresAt(fixedInstant.minusSeconds(1)); // expired 1s ago

        CreateBookingRequestDto request = CreateBookingRequestDto.of(1L, 100L, List.of(1L, 2L), "idemp-expired");

        when(timeSlotRepository.findByIdWithLock(100L)).thenReturn(Optional.of(timeSlot));
        when(bookingQueueRepository.findBySessionDateAndSessionType(LocalDate.of(2026, 10, 15), SessionType.MORNING))
                .thenReturn(Optional.of(queue));
        when(queueEntryRepository.findByQueueIdAndStudentId(200L, 1L))
                .thenReturn(Optional.of(queueEntry));

        assertThatThrownBy(() -> bookingService.confirmBooking(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");

        assertThat(queueEntry.getStatus()).isEqualTo(QueueEntryStatus.EXPIRED);
    }

    @Test
    @DisplayName("Should reject booking if any group member has 0 weekly credits")
    void shouldRejectIfMemberHasZeroCredits() {
        student2.setWeeklyCredits(0); // Bob has 0 credits

        CreateBookingRequestDto request = CreateBookingRequestDto.of(1L, 100L, List.of(1L, 2L), "idemp-zero-credit");

        when(timeSlotRepository.findByIdWithLock(100L)).thenReturn(Optional.of(timeSlot));
        when(bookingQueueRepository.findBySessionDateAndSessionType(LocalDate.of(2026, 10, 15), SessionType.MORNING))
                .thenReturn(Optional.of(queue));
        when(queueEntryRepository.findByQueueIdAndStudentId(200L, 1L))
                .thenReturn(Optional.of(queueEntry));

        when(studentRepository.findByIdWithLock(1L)).thenReturn(Optional.of(student1));
        when(studentRepository.findByIdWithLock(2L)).thenReturn(Optional.of(student2));

        assertThatThrownBy(() -> bookingService.confirmBooking(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("insufficient weekly credits");

        // Neither student should have credits deducted
        assertThat(student1.getWeeklyCredits()).isEqualTo(6);
        assertThat(student2.getWeeklyCredits()).isEqualTo(0);
        assertThat(timeSlot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should reject booking if any group member is inactive")
    void shouldRejectIfMemberIsInactive() {
        student2.setActive(false); // Inactive student

        CreateBookingRequestDto request = CreateBookingRequestDto.of(1L, 100L, List.of(1L, 2L), "idemp-inactive");

        when(timeSlotRepository.findByIdWithLock(100L)).thenReturn(Optional.of(timeSlot));
        when(bookingQueueRepository.findBySessionDateAndSessionType(LocalDate.of(2026, 10, 15), SessionType.MORNING))
                .thenReturn(Optional.of(queue));
        when(queueEntryRepository.findByQueueIdAndStudentId(200L, 1L))
                .thenReturn(Optional.of(queueEntry));

        when(studentRepository.findByIdWithLock(1L)).thenReturn(Optional.of(student1));
        when(studentRepository.findByIdWithLock(2L)).thenReturn(Optional.of(student2));

        assertThatThrownBy(() -> bookingService.confirmBooking(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inactive and not eligible");
    }

    @Test
    @DisplayName("Should reject booking if any group member has an overlapping booking")
    void shouldRejectIfMemberHasConflictingBooking() {
        CreateBookingRequestDto request = CreateBookingRequestDto.of(1L, 100L, List.of(1L, 2L), "idemp-conflict");

        when(timeSlotRepository.findByIdWithLock(100L)).thenReturn(Optional.of(timeSlot));
        when(bookingQueueRepository.findBySessionDateAndSessionType(LocalDate.of(2026, 10, 15), SessionType.MORNING))
                .thenReturn(Optional.of(queue));
        when(queueEntryRepository.findByQueueIdAndStudentId(200L, 1L))
                .thenReturn(Optional.of(queueEntry));

        when(studentRepository.findByIdWithLock(1L)).thenReturn(Optional.of(student1));
        when(studentRepository.findByIdWithLock(2L)).thenReturn(Optional.of(student2));

        // Simulate student 1 having no conflict and student 2 already having a booking at this time
        when(bookingPlayerRepository.existsByStudentIdAndSlotStartTimeAndBookingStatus(
                1L, timeSlot.getStartTime(), BookingStatus.CONFIRMED))
                .thenReturn(false);
        when(bookingPlayerRepository.existsByStudentIdAndSlotStartTimeAndBookingStatus(
                2L, timeSlot.getStartTime(), BookingStatus.CONFIRMED))
                .thenReturn(true);

        assertThatThrownBy(() -> bookingService.confirmBooking(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already has a confirmed booking");

        assertThat(student1.getWeeklyCredits()).isEqualTo(6);
        assertThat(student2.getWeeklyCredits()).isEqualTo(4);
    }

    @Test
    @DisplayName("Idempotent retry with existing idempotencyKey should return existing booking without double deduction")
    void shouldReturnExistingBookingOnIdempotentRetry() {
        Booking existingBooking = new Booking(timeSlot, student1, queueEntry, "idemp-repeat");
        existingBooking.setId(999L);
        existingBooking.addPlayer(new BookingPlayer(existingBooking, student1, 1, "QR_1"));
        existingBooking.addPlayer(new BookingPlayer(existingBooking, student2, 2, "QR_2"));

        when(bookingRepository.findByIdempotencyKey("idemp-repeat")).thenReturn(Optional.of(existingBooking));

        CreateBookingRequestDto request = CreateBookingRequestDto.of(1L, 100L, List.of(1L, 2L), "idemp-repeat");

        BookingResponseDto response = bookingService.confirmBooking(request);

        assertThat(response.bookingId()).isEqualTo(999L);
        // Verify timeSlotRepository and studentRepository were not even touched
        verify(timeSlotRepository, never()).findByIdWithLock(any());
        verify(studentRepository, never()).findByIdWithLock(any());
    }

    @Test
    @DisplayName("Should reject booking when student has not joined the queue (queue bypass prevention)")
    void shouldRejectBookingWhenStudentHasNoQueueEntry() {
        CreateBookingRequestDto request = CreateBookingRequestDto.of(1L, 100L, List.of(1L, 2L), "idemp-noqueue");

        when(timeSlotRepository.findByIdWithLock(100L)).thenReturn(Optional.of(timeSlot));
        when(bookingQueueRepository.findBySessionDateAndSessionType(LocalDate.of(2026, 10, 15), SessionType.MORNING))
                .thenReturn(Optional.of(queue));
        when(queueEntryRepository.findByQueueIdAndStudentId(200L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.confirmBooking(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not registered in the booking queue");
    }

    @Test
    @DisplayName("Should reject booking when student queue status is WAITING rather than ADMITTED")
    void shouldRejectBookingWhenQueueStatusNotAdmitted() {
        QueueEntry waitingEntry = new QueueEntry(queue, student1, fixedInstant);
        waitingEntry.setId(301L); // Status defaults to WAITING

        CreateBookingRequestDto request = CreateBookingRequestDto.of(1L, 100L, List.of(1L, 2L), "idemp-waiting");

        when(timeSlotRepository.findByIdWithLock(100L)).thenReturn(Optional.of(timeSlot));
        when(bookingQueueRepository.findBySessionDateAndSessionType(LocalDate.of(2026, 10, 15), SessionType.MORNING))
                .thenReturn(Optional.of(queue));
        when(queueEntryRepository.findByQueueIdAndStudentId(200L, 1L))
                .thenReturn(Optional.of(waitingEntry));

        assertThatThrownBy(() -> bookingService.confirmBooking(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not hold an active booking opportunity");
    }

    @Test
    @DisplayName("Should reject booking when 60-second booking opportunity window has expired")
    void shouldRejectBookingWhenQueueOpportunityHasExpired() {
        QueueEntry expiredEntry = new QueueEntry(queue, student1, fixedInstant.minusSeconds(70));
        expiredEntry.setId(302L);
        expiredEntry.admit(fixedInstant.minusSeconds(70)); // Admitted 70 seconds ago -> expired at +60s = 10s ago

        CreateBookingRequestDto request = CreateBookingRequestDto.of(1L, 100L, List.of(1L, 2L), "idemp-expired");

        when(timeSlotRepository.findByIdWithLock(100L)).thenReturn(Optional.of(timeSlot));
        when(bookingQueueRepository.findBySessionDateAndSessionType(LocalDate.of(2026, 10, 15), SessionType.MORNING))
                .thenReturn(Optional.of(queue));
        when(queueEntryRepository.findByQueueIdAndStudentId(200L, 1L))
                .thenReturn(Optional.of(expiredEntry));

        assertThatThrownBy(() -> bookingService.confirmBooking(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Booking opportunity has expired");
    }
}
