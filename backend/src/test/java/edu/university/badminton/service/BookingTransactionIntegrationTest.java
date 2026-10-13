package edu.university.badminton.service;

import edu.university.badminton.dto.CreateBookingRequestDto;
import edu.university.badminton.model.Booking;
import edu.university.badminton.model.BookingPlayer;
import edu.university.badminton.model.BookingQueue;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class BookingTransactionIntegrationTest {

    @Autowired
    private BookingService bookingService;

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

    private Student student1;
    private Student student2;
    private Student student3;
    private Student studentZeroCredit;

    private Court court;
    private SessionConfig sessionConfig;
    private TimeSlot timeSlot;
    private BookingQueue queue;
    private QueueEntry queueEntry1;

    @BeforeEach
    void setUp() {
        bookingPlayerRepository.deleteAll();
        bookingRepository.deleteAll();
        queueEntryRepository.deleteAll();
        bookingQueueRepository.deleteAll();
        timeSlotRepository.deleteAll();
        courtRepository.deleteAll();
        sessionConfigRepository.deleteAll();
        studentRepository.deleteAll();

        student1 = studentRepository.save(new Student("Alice", "alice@test.edu", "AD-001", Role.STUDENT));
        student2 = studentRepository.save(new Student("Bob", "bob@test.edu", "AD-002", Role.STUDENT));
        student3 = studentRepository.save(new Student("Charlie", "charlie@test.edu", "AD-003", Role.STUDENT));

        studentZeroCredit = new Student("Dana", "dana@test.edu", "AD-004", Role.STUDENT);
        studentZeroCredit.setWeeklyCredits(0);
        studentZeroCredit = studentRepository.save(studentZeroCredit);

        court = courtRepository.save(new Court(1));
        sessionConfig = sessionConfigRepository.save(
                new SessionConfig(SessionType.MORNING, LocalTime.of(6, 0), LocalTime.of(8, 30), 30, LocalTime.of(21, 0), LocalTime.of(21, 5), 1)
        );

        LocalDate date = LocalDate.of(2026, 10, 20);
        LocalDateTime start = LocalDateTime.of(date, LocalTime.of(6, 0));
        LocalDateTime end = LocalDateTime.of(date, LocalTime.of(6, 30));

        timeSlot = timeSlotRepository.save(
                new TimeSlot(court, sessionConfig, date, start, end, SlotStatus.AVAILABLE)
        );

        queue = bookingQueueRepository.save(new BookingQueue(sessionConfig, SessionType.MORNING, date));

        queueEntry1 = new QueueEntry(queue, student1, Instant.now());
        queueEntry1.admit(Instant.now());
        queueEntry1 = queueEntryRepository.save(queueEntry1);
    }

    @Test
    @DisplayName("Complete atomic transaction: persists booking, deducts 1 credit per member, reserves slot, completes queue entry")
    void shouldPersistBookingAndDeductCreditsAtomically() {
        CreateBookingRequestDto request = CreateBookingRequestDto.of(
                student1.getId(),
                timeSlot.getId(),
                List.of(student1.getId(), student2.getId(), student3.getId()),
                "idemp-integration-1"
        );

        var response = bookingService.confirmBooking(request);

        assertThat(response).isNotNull();
        assertThat(response.players()).hasSize(3);

        // Verify Student 1 (6 -> 5)
        Student refreshed1 = studentRepository.findById(student1.getId()).orElseThrow();
        assertThat(refreshed1.getWeeklyCredits()).isEqualTo(5);

        // Verify Student 2 (6 -> 5)
        Student refreshed2 = studentRepository.findById(student2.getId()).orElseThrow();
        assertThat(refreshed2.getWeeklyCredits()).isEqualTo(5);

        // Verify Student 3 (6 -> 5)
        Student refreshed3 = studentRepository.findById(student3.getId()).orElseThrow();
        assertThat(refreshed3.getWeeklyCredits()).isEqualTo(5);

        // Verify TimeSlot is BOOKED
        TimeSlot refreshedSlot = timeSlotRepository.findById(timeSlot.getId()).orElseThrow();
        assertThat(refreshedSlot.getStatus()).isEqualTo(SlotStatus.BOOKED);

        // Verify QueueEntry is COMPLETED
        QueueEntry refreshedEntry = queueEntryRepository.findById(queueEntry1.getId()).orElseThrow();
        assertThat(refreshedEntry.getStatus()).isEqualTo(QueueEntryStatus.COMPLETED);

        // Verify Booking in DB
        List<Booking> allBookings = bookingRepository.findAll();
        assertThat(allBookings).hasSize(1);
        List<BookingPlayer> savedPlayers = bookingPlayerRepository.findByBookingId(allBookings.get(0).getId());
        assertThat(savedPlayers).hasSize(3);
    }

    @Test
    @DisplayName("Transaction rollback: if any group member has 0 credits, all changes roll back")
    void shouldRollbackWhenMemberHasZeroCredits() {
        CreateBookingRequestDto request = CreateBookingRequestDto.of(
                student1.getId(),
                timeSlot.getId(),
                List.of(student1.getId(), student2.getId(), studentZeroCredit.getId()),
                "idemp-zero-credit"
        );

        assertThatThrownBy(() -> bookingService.confirmBooking(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("insufficient weekly credits");

        // Verify rollback: Alice's credits were NOT deducted (still 6)
        Student refreshed1 = studentRepository.findById(student1.getId()).orElseThrow();
        assertThat(refreshed1.getWeeklyCredits()).isEqualTo(6);

        // Verify rollback: Bob's credits were NOT deducted (still 6)
        Student refreshed2 = studentRepository.findById(student2.getId()).orElseThrow();
        assertThat(refreshed2.getWeeklyCredits()).isEqualTo(6);

        // Verify rollback: Dana still has 0
        Student refreshedDana = studentRepository.findById(studentZeroCredit.getId()).orElseThrow();
        assertThat(refreshedDana.getWeeklyCredits()).isEqualTo(0);

        // Verify rollback: TimeSlot is still AVAILABLE
        TimeSlot refreshedSlot = timeSlotRepository.findById(timeSlot.getId()).orElseThrow();
        assertThat(refreshedSlot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);

        // Verify rollback: QueueEntry is still ADMITTED
        QueueEntry refreshedEntry = queueEntryRepository.findById(queueEntry1.getId()).orElseThrow();
        assertThat(refreshedEntry.getStatus()).isEqualTo(QueueEntryStatus.ADMITTED);

        // Verify rollback: No bookings created
        assertThat(bookingRepository.findAll()).isEmpty();
        assertThat(bookingPlayerRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Concurrent attempts on the same slot: exactly one succeeds and the other rolls back")
    void shouldHandleConcurrentBookingAttemptsAtomically() throws Exception {
        // Create second student admitted in queue
        Student studentB1 = studentRepository.save(new Student("Beta1", "beta1@test.edu", "AD-011", Role.STUDENT));
        Student studentB2 = studentRepository.save(new Student("Beta2", "beta2@test.edu", "AD-012", Role.STUDENT));

        QueueEntry queueEntryB = new QueueEntry(queue, studentB1, Instant.now());
        queueEntryB.admit(Instant.now());
        queueEntryRepository.save(queueEntryB);

        CreateBookingRequestDto requestA = CreateBookingRequestDto.of(
                student1.getId(), timeSlot.getId(), List.of(student1.getId(), student2.getId()), "idemp-A"
        );
        CreateBookingRequestDto requestB = CreateBookingRequestDto.of(
                studentB1.getId(), timeSlot.getId(), List.of(studentB1.getId(), studentB2.getId()), "idemp-B"
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                bookingService.confirmBooking(requestA);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                bookingService.confirmBooking(requestB);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // Exactly one should succeed, one should fail
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);

        // Exactly one booking in DB
        assertThat(bookingRepository.findAll()).hasSize(1);
    }
}
