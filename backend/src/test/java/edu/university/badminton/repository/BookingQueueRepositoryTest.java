package edu.university.badminton.repository;

import edu.university.badminton.model.BookingQueue;
import edu.university.badminton.model.QueueEntry;
import edu.university.badminton.model.QueueEntryStatus;
import edu.university.badminton.model.Role;
import edu.university.badminton.model.SessionConfig;
import edu.university.badminton.model.SessionType;
import edu.university.badminton.model.Student;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class BookingQueueRepositoryTest {

    @Autowired
    private BookingQueueRepository bookingQueueRepository;

    @Autowired
    private QueueEntryRepository queueEntryRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private SessionConfigRepository sessionConfigRepository;

    private SessionConfig morningConfig;
    private Student student1;
    private Student student2;
    private LocalDate targetDate;

    @BeforeEach
    void setUp() {
        morningConfig = sessionConfigRepository.saveAndFlush(new SessionConfig(
                SessionType.MORNING,
                LocalTime.of(6, 0),
                LocalTime.of(8, 30),
                30,
                LocalTime.of(21, 0),
                LocalTime.of(21, 5),
                1
        ));

        student1 = studentRepository.saveAndFlush(new Student(
                "Alice", "alice@university.edu", "S101", Role.STUDENT
        ));

        student2 = studentRepository.saveAndFlush(new Student(
                "Bob", "bob@university.edu", "S102", Role.STUDENT
        ));

        targetDate = LocalDate.of(2026, 10, 15);
    }

    @Test
    @DisplayName("Should persist and find booking queue by date and session type")
    void shouldPersistAndFindBookingQueue() {
        BookingQueue queue = new BookingQueue(morningConfig, SessionType.MORNING, targetDate);
        BookingQueue saved = bookingQueueRepository.saveAndFlush(queue);

        assertThat(saved.getId()).isNotNull();

        Optional<BookingQueue> found = bookingQueueRepository.findBySessionDateAndSessionType(targetDate, SessionType.MORNING);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("Should enforce uniqueness constraint preventing duplicate queue for same date and session")
    void shouldRejectDuplicateQueueForSameDateAndSession() {
        BookingQueue queue1 = new BookingQueue(morningConfig, SessionType.MORNING, targetDate);
        bookingQueueRepository.saveAndFlush(queue1);

        BookingQueue queue2 = new BookingQueue(morningConfig, SessionType.MORNING, targetDate);

        assertThatThrownBy(() -> bookingQueueRepository.saveAndFlush(queue2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should enforce uniqueness constraint preventing duplicate entry for same student in a queue")
    void shouldRejectDuplicateQueueEntryForSameStudent() {
        BookingQueue queue = bookingQueueRepository.saveAndFlush(
                new BookingQueue(morningConfig, SessionType.MORNING, targetDate)
        );

        QueueEntry entry1 = new QueueEntry(queue, student1, Instant.now());
        queueEntryRepository.saveAndFlush(entry1);

        QueueEntry duplicateEntry = new QueueEntry(queue, student1, Instant.now());

        assertThatThrownBy(() -> queueEntryRepository.saveAndFlush(duplicateEntry))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should retrieve waiting students in strict FIFO order based on joined timestamp and ID")
    void shouldRetrieveInFifoOrder() {
        BookingQueue queue = bookingQueueRepository.saveAndFlush(
                new BookingQueue(morningConfig, SessionType.MORNING, targetDate)
        );

        Instant t0 = Instant.parse("2026-10-14T21:00:00Z");
        Instant t1 = Instant.parse("2026-10-14T21:00:05Z");

        QueueEntry entry1 = queueEntryRepository.saveAndFlush(new QueueEntry(queue, student1, t0));
        QueueEntry entry2 = queueEntryRepository.saveAndFlush(new QueueEntry(queue, student2, t1));

        Optional<QueueEntry> first = queueEntryRepository.findFirstByQueueIdAndStatusOrderByJoinedAtAscIdAsc(
                queue.getId(), QueueEntryStatus.WAITING
        );

        assertThat(first).isPresent();
        assertThat(first.get().getId()).isEqualTo(entry1.getId());
        assertThat(first.get().getStudent().getId()).isEqualTo(student1.getId());
    }

    @Test
    @DisplayName("Should correctly calculate 1-based queue waiting position")
    void shouldCalculateWaitingPosition() {
        BookingQueue queue = bookingQueueRepository.saveAndFlush(
                new BookingQueue(morningConfig, SessionType.MORNING, targetDate)
        );

        Instant t0 = Instant.parse("2026-10-14T21:00:00Z");
        Instant t1 = Instant.parse("2026-10-14T21:00:05Z");

        QueueEntry entry1 = queueEntryRepository.saveAndFlush(new QueueEntry(queue, student1, t0));
        QueueEntry entry2 = queueEntryRepository.saveAndFlush(new QueueEntry(queue, student2, t1));

        long pos1 = queueEntryRepository.calculateWaitingPosition(queue.getId(), entry1.getJoinedAt(), entry1.getId());
        long pos2 = queueEntryRepository.calculateWaitingPosition(queue.getId(), entry2.getJoinedAt(), entry2.getId());

        assertThat(pos1).isEqualTo(1L);
        assertThat(pos2).isEqualTo(2L);
    }

    @Test
    @DisplayName("Should acquire pessimistic write lock on queue row")
    void shouldAcquirePessimisticWriteLock() {
        BookingQueue queue = bookingQueueRepository.saveAndFlush(
                new BookingQueue(morningConfig, SessionType.MORNING, targetDate)
        );

        Optional<BookingQueue> locked = bookingQueueRepository.findBySessionDateAndSessionTypeForUpdate(targetDate, SessionType.MORNING);
        assertThat(locked).isPresent();
        assertThat(locked.get().getId()).isEqualTo(queue.getId());
    }
}
