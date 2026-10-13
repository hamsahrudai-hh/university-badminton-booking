package edu.university.badminton.service;

import edu.university.badminton.dto.AdmitNextResponseDto;
import edu.university.badminton.dto.JoinQueueRequestDto;
import edu.university.badminton.model.Role;
import edu.university.badminton.model.SessionConfig;
import edu.university.badminton.model.SessionType;
import edu.university.badminton.model.Student;
import edu.university.badminton.repository.BookingQueueRepository;
import edu.university.badminton.repository.QueueEntryRepository;
import edu.university.badminton.repository.SessionConfigRepository;
import edu.university.badminton.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
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
class BookingQueueConcurrencyTest {

    @Autowired
    private BookingQueueService bookingQueueService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private SessionConfigRepository sessionConfigRepository;

    @Autowired
    private BookingQueueRepository bookingQueueRepository;

    @Autowired
    private QueueEntryRepository queueEntryRepository;

    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        queueEntryRepository.deleteAll();
        bookingQueueRepository.deleteAll();

        testDate = LocalDate.now().plusDays(1);

        if (!sessionConfigRepository.existsBySessionType(SessionType.MORNING)) {
            sessionConfigRepository.save(new SessionConfig(
                    SessionType.MORNING,
                    LocalTime.of(6, 0),
                    LocalTime.of(8, 30),
                    30,
                    LocalTime.of(0, 0), // open immediately for testing
                    LocalTime.of(0, 5),
                    2
            ));
        }
    }

    @Test
    @DisplayName("Concurrent admission requests should serialize and allow only one active opportunity")
    void shouldPreventConcurrentAdmissions() throws Exception {
        // Create 2 students
        Student student1 = studentRepository.save(new Student("Concurrent Alice", "calice@uni.edu", "C101", Role.STUDENT));
        Student student2 = studentRepository.save(new Student("Concurrent Bob", "cbob@uni.edu", "C102", Role.STUDENT));

        // Join both students to queue
        bookingQueueService.joinQueue(new JoinQueueRequestDto(student1.getId(), testDate, SessionType.MORNING));
        bookingQueueService.joinQueue(new JoinQueueRequestDto(student2.getId(), testDate, SessionType.MORNING));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Callable<AdmitNextResponseDto>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                readyLatch.countDown();
                startLatch.await();
                return bookingQueueService.admitNextStudent(testDate, SessionType.MORNING);
            });
        }

        List<Future<AdmitNextResponseDto>> futures = new ArrayList<>();
        for (Callable<AdmitNextResponseDto> task : tasks) {
            futures.add(executor.submit(task));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Launch both threads simultaneously

        List<AdmitNextResponseDto> results = new ArrayList<>();
        for (Future<AdmitNextResponseDto> future : futures) {
            results.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();

        // Exactly one thread must have successfully admitted a student (true)
        // and the other thread must have recognized the active window (false)
        long admittedCount = results.stream().filter(AdmitNextResponseDto::studentAdmitted).count();
        assertThat(admittedCount).isEqualTo(1L);

        // The active opportunity must be for student 1 (first in FIFO line)
        AdmitNextResponseDto successfulAdmission = results.stream()
                .filter(AdmitNextResponseDto::studentAdmitted)
                .findFirst()
                .orElseThrow();
        assertThat(successfulAdmission.studentId()).isEqualTo(student1.getId());
    }

    @Test
    @DisplayName("Simultaneous first-time join requests should safely resolve queue creation race without duplicate errors")
    void shouldHandleConcurrentFirstTimeQueueCreation() throws Exception {
        LocalDate freshDate = LocalDate.now().plusDays(2);

        Student studentA = studentRepository.save(new Student("Racer Alice", "ralice@uni.edu", "R101", Role.STUDENT));
        Student studentB = studentRepository.save(new Student("Racer Bob", "rbob@uni.edu", "R102", Role.STUDENT));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Callable<Boolean>> tasks = List.of(
                () -> {
                    readyLatch.countDown();
                    startLatch.await();
                    bookingQueueService.joinQueue(new JoinQueueRequestDto(studentA.getId(), freshDate, SessionType.MORNING));
                    return true;
                },
                () -> {
                    readyLatch.countDown();
                    startLatch.await();
                    bookingQueueService.joinQueue(new JoinQueueRequestDto(studentB.getId(), freshDate, SessionType.MORNING));
                    return true;
                }
        );

        List<Future<Boolean>> futures = new ArrayList<>();
        for (Callable<Boolean> task : tasks) {
            futures.add(executor.submit(task));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Launch both joins concurrently

        for (Future<Boolean> future : futures) {
            assertThat(future.get(10, TimeUnit.SECONDS)).isTrue();
        }
        executor.shutdown();

        // Verify that exactly 1 queue was created and both students successfully joined
        var queueOpt = bookingQueueRepository.findBySessionDateAndSessionType(freshDate, SessionType.MORNING);
        assertThat(queueOpt).isPresent();
        assertThat(queueEntryRepository.countByQueueId(queueOpt.get().getId())).isEqualTo(2L);
    }
}
