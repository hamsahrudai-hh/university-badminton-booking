package edu.university.badminton.service;

import edu.university.badminton.dto.NoShowProcessingResponseDto;
import edu.university.badminton.model.Booking;
import edu.university.badminton.model.BookingPlayer;
import edu.university.badminton.model.BookingStatus;
import edu.university.badminton.model.Court;
import edu.university.badminton.model.Role;
import edu.university.badminton.model.SessionConfig;
import edu.university.badminton.model.SessionType;
import edu.university.badminton.model.SlotStatus;
import edu.university.badminton.model.Student;
import edu.university.badminton.model.TimeSlot;
import edu.university.badminton.repository.BookingPlayerRepository;
import edu.university.badminton.repository.BookingRepository;
import edu.university.badminton.repository.CourtRepository;
import edu.university.badminton.repository.SessionConfigRepository;
import edu.university.badminton.repository.StudentRepository;
import edu.university.badminton.repository.TimeSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NoShowConcurrencyTest {

    @Autowired
    private CheckInService checkInService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourtRepository courtRepository;

    @Autowired
    private SessionConfigRepository sessionConfigRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingPlayerRepository bookingPlayerRepository;

    private Student testStudent;
    private Court testCourt;
    private SessionConfig testConfig;
    private TimeSlot testSlot;
    private Booking testBooking;
    private BookingPlayer testPlayer;

    @BeforeEach
    void setUp() {
        bookingPlayerRepository.deleteAll();
        bookingRepository.deleteAll();

        // 1. Create or retrieve Court
        testCourt = courtRepository.findByCourtNumber(1)
                .orElseGet(() -> courtRepository.save(new Court(1)));

        // 2. Create or retrieve SessionConfig
        testConfig = sessionConfigRepository.findBySessionType(SessionType.MORNING)
                .orElseGet(() -> sessionConfigRepository.save(new SessionConfig(
                        SessionType.MORNING,
                        LocalTime.of(6, 0),
                        LocalTime.of(8, 30),
                        30,
                        LocalTime.of(21, 0),
                        LocalTime.of(21, 5),
                        1
                )));

        // 3. Create TimeSlot in past (e.g. yesterday)
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime pastStart = LocalDateTime.of(yesterday, LocalTime.of(6, 0));
        LocalDateTime pastEnd = LocalDateTime.of(yesterday, LocalTime.of(6, 30));

        testSlot = new TimeSlot(testCourt, testConfig, yesterday, pastStart, pastEnd, SlotStatus.BOOKED);
        testSlot = timeSlotRepository.save(testSlot);

        // 4. Create Student with 5 credits and 100.0 reliability
        testStudent = new Student("Concurrent Test Student", "concurrent.test@vitapstudent.ac.in", "23TESTCONCUR", Role.STUDENT);
        testStudent.setWeeklyCredits(5);
        testStudent.setReliabilityRating(100.0);
        testStudent.setActive(true);
        testStudent.setEmailVerified(true);
        testStudent = studentRepository.save(testStudent);

        // 5. Create Booking & BookingPlayer (un-checked-in, noShow = false)
        testBooking = new Booking(testSlot, testStudent, null, "idemp_concur_" + System.currentTimeMillis());
        testBooking.setStatus(BookingStatus.CONFIRMED);
        testBooking = bookingRepository.save(testBooking);

        testPlayer = new BookingPlayer(testBooking, testStudent, 1, "QR_CONCUR_TEST_" + System.currentTimeMillis());
        testPlayer.setCheckedIn(false);
        testPlayer.setNoShow(false);
        testPlayer = bookingPlayerRepository.save(testPlayer);
    }

    @Test
    @DisplayName("Concurrent manual staff trigger and background scheduler runs do NOT apply duplicate penalties")
    void shouldPreventDuplicatePenaltiesUnderConcurrentExecution() throws Exception {
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Callable<NoShowProcessingResponseDto>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final String actorName = i == 0 ? "STAFF_MARSHAL_1" : "SYSTEM_AUTOMATIC_SCHEDULER_" + i;
            tasks.add(() -> {
                readyLatch.countDown();
                // Wait for all threads to be ready, then release simultaneously
                startLatch.await();
                return checkInService.processNoShows(null, actorName);
            });
        }

        List<Future<NoShowProcessingResponseDto>> futures = new ArrayList<>();
        for (Callable<NoShowProcessingResponseDto> task : tasks) {
            futures.add(executor.submit(task));
        }

        // Wait until all threads are queued up, then trigger them concurrently
        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        int totalPenalizedAcrossThreads = 0;
        for (Future<NoShowProcessingResponseDto> future : futures) {
            NoShowProcessingResponseDto res = future.get(10, TimeUnit.SECONDS);
            totalPenalizedAcrossThreads += res.processedCount();
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Exactly one thread must have processed the penalty; other threads must have skipped it
        assertThat(totalPenalizedAcrossThreads).isEqualTo(1);

        // Verify Student in database: credits forfeited to 0 (once), reliability reduced by exactly 10 (not 40!)
        Student freshStudent = studentRepository.findById(testStudent.getId()).orElseThrow();
        assertThat(freshStudent.getWeeklyCredits()).isEqualTo(0);
        assertThat(freshStudent.getReliabilityRating()).isEqualTo(90.0);

        // Verify BookingPlayer in database: marked as noShow = true
        BookingPlayer freshPlayer = bookingPlayerRepository.findById(testPlayer.getId()).orElseThrow();
        assertThat(freshPlayer.getNoShow()).isTrue();
        assertThat(freshPlayer.getNoShowProcessedAt()).isNotNull();
    }
}
