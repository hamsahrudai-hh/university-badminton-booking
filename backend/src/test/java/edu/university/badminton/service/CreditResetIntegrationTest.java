package edu.university.badminton.service;

import edu.university.badminton.dto.CreditResetResultDto;
import edu.university.badminton.model.CreditResetExecution;
import edu.university.badminton.model.Role;
import edu.university.badminton.model.Student;
import edu.university.badminton.repository.CreditResetExecutionRepository;
import edu.university.badminton.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CreditResetIntegrationTest {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CreditResetExecutionRepository creditResetExecutionRepository;

    @Autowired
    private CreditResetService creditResetService;

    @Autowired
    private edu.university.badminton.repository.BookingPlayerRepository bookingPlayerRepository;

    @Autowired
    private edu.university.badminton.repository.BookingRepository bookingRepository;

    @Autowired
    private edu.university.badminton.repository.QueueEntryRepository queueEntryRepository;

    @Autowired
    private edu.university.badminton.repository.EmailVerificationTokenRepository emailVerificationTokenRepository;

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Kolkata");

    private Student activeStudent1;
    private Student activePenalizedStudent;
    private Student activeFullCreditStudent;
    private Student inactiveUnverifiedStudent;
    private Student staffUser;

    @BeforeEach
    void setUp() {
        bookingPlayerRepository.deleteAll();
        bookingRepository.deleteAll();
        queueEntryRepository.deleteAll();
        emailVerificationTokenRepository.deleteAll();
        creditResetExecutionRepository.deleteAll();
        studentRepository.deleteAll();

        // 1. Active student with reduced balance (e.g. 2 credits)
        activeStudent1 = new Student("Alice Student", "alice@vitapstudent.ac.in", "23BCE0001", Role.STUDENT);
        activeStudent1.setWeeklyCredits(2);
        activeStudent1 = studentRepository.save(activeStudent1);

        // 2. Active student penalized by no-show (0 credits)
        activePenalizedStudent = new Student("Bob Penalized", "bob@vitapstudent.ac.in", "23BCE0002", Role.STUDENT);
        activePenalizedStudent.setWeeklyCredits(0);
        activePenalizedStudent = studentRepository.save(activePenalizedStudent);

        // 3. Active student who hasn't played (6 credits)
        activeFullCreditStudent = new Student("Charlie Full", "charlie@vitapstudent.ac.in", "23BCE0003", Role.STUDENT);
        activeFullCreditStudent.setWeeklyCredits(6);
        activeFullCreditStudent = studentRepository.save(activeFullCreditStudent);

        // 4. Inactive / unverified student (0 credits)
        inactiveUnverifiedStudent = new Student("Dana Inactive", "dana@vitapstudent.ac.in", "23BCE0004", Role.STUDENT);
        inactiveUnverifiedStudent.setActive(false);
        inactiveUnverifiedStudent.setEmailVerified(false);
        inactiveUnverifiedStudent.setWeeklyCredits(0);
        inactiveUnverifiedStudent = studentRepository.save(inactiveUnverifiedStudent);

        // 5. Staff user (Staff role)
        staffUser = new Student("Staff Member", "staff@vitapstudent.ac.in", "ST-0001", Role.STAFF);
        staffUser.setWeeklyCredits(6);
        staffUser = studentRepository.save(staffUser);
    }

    @Test
    @DisplayName("End-to-end weekly credit reset updates eligible students, restores no-show penalties, and deduplicates")
    void shouldExecuteWeeklyCreditResetWithDeduplication() {
        // Fix clock to Monday of Week 43 (2026-10-19 00:00:00 IST)
        Instant mondayWeek43 = ZonedDateTime.of(2026, 10, 19, 0, 0, 0, 0, ZONE_ID).toInstant();

        // 1. First execution of Week 43
        CreditResetResultDto result1 = creditResetService.resetWeeklyCredits(mondayWeek43, "SYSTEM_AUTOMATIC_SCHEDULER");

        assertThat(result1.executed()).isTrue();
        assertThat(result1.alreadyExecuted()).isFalse();
        assertThat(result1.periodKey()).isEqualTo("2026-W43");
        // Eligible active students: Alice, Bob, Charlie (3 students)
        assertThat(result1.studentsResetCount()).isEqualTo(3);

        // Verify database state after reset
        Student refreshedAlice = studentRepository.findById(activeStudent1.getId()).orElseThrow();
        Student refreshedBob = studentRepository.findById(activePenalizedStudent.getId()).orElseThrow();
        Student refreshedCharlie = studentRepository.findById(activeFullCreditStudent.getId()).orElseThrow();
        Student refreshedDana = studentRepository.findById(inactiveUnverifiedStudent.getId()).orElseThrow();
        Student refreshedStaff = studentRepository.findById(staffUser.getId()).orElseThrow();

        assertThat(refreshedAlice.getWeeklyCredits()).isEqualTo(6);
        assertThat(refreshedBob.getWeeklyCredits()).isEqualTo(6); // No-show penalty restored to 6 for the new week
        assertThat(refreshedCharlie.getWeeklyCredits()).isEqualTo(6); // Stayed at 6, never extra
        assertThat(refreshedDana.getWeeklyCredits()).isEqualTo(0); // Inactive/unverified remains 0
        assertThat(refreshedStaff.getWeeklyCredits()).isEqualTo(6);

        // Verify execution audit table
        Optional<CreditResetExecution> execution = creditResetExecutionRepository.findByPeriodKey("2026-W43");
        assertThat(execution).isPresent();
        assertThat(execution.get().getStudentsResetCount()).isEqualTo(3);
        assertThat(execution.get().getStatus()).isEqualTo("SUCCESS");

        // 2. Simulate a mid-week booking on Monday afternoon: Alice books a court and deducts 1 credit
        refreshedAlice.setWeeklyCredits(5);
        studentRepository.save(refreshedAlice);

        // 3. Second execution in same week (simulating server restart or duplicate scheduler tick on Tuesday)
        Instant tuesdayWeek43 = ZonedDateTime.of(2026, 10, 20, 10, 30, 0, 0, ZONE_ID).toInstant();

        CreditResetResultDto result2 = creditResetService.resetWeeklyCredits(tuesdayWeek43, "SYSTEM_AUTOMATIC_SCHEDULER");

        assertThat(result2.executed()).isFalse();
        assertThat(result2.alreadyExecuted()).isTrue();
        assertThat(result2.periodKey()).isEqualTo("2026-W43");

        // Verify Alice's mid-week credit balance of 5 was NOT overwritten!
        Student aliceAfterRestart = studentRepository.findById(activeStudent1.getId()).orElseThrow();
        assertThat(aliceAfterRestart.getWeeklyCredits()).isEqualTo(5);

        // 4. Fast-forward to the NEXT week (Monday of Week 44, 2026-10-26 00:00:00 IST)
        Instant mondayWeek44 = ZonedDateTime.of(2026, 10, 26, 0, 0, 0, 0, ZONE_ID).toInstant();

        CreditResetResultDto result3 = creditResetService.resetWeeklyCredits(mondayWeek44, "SYSTEM_AUTOMATIC_SCHEDULER");

        assertThat(result3.executed()).isTrue();
        assertThat(result3.alreadyExecuted()).isFalse();
        assertThat(result3.periodKey()).isEqualTo("2026-W44");

        // Now Alice is reset to 6 for Week 44
        Student aliceWeek44 = studentRepository.findById(activeStudent1.getId()).orElseThrow();
        assertThat(aliceWeek44.getWeeklyCredits()).isEqualTo(6);

        // Both execution records exist
        assertThat(creditResetExecutionRepository.existsByPeriodKey("2026-W43")).isTrue();
        assertThat(creditResetExecutionRepository.existsByPeriodKey("2026-W44")).isTrue();
    }

    @Test
    @DisplayName("Concurrent manual staff trigger and background scheduler runs do NOT apply duplicate resets")
    void shouldPreventDuplicateResetsUnderConcurrentExecution() throws Exception {
        Instant mondayWeek43 = ZonedDateTime.of(2026, 10, 19, 0, 0, 0, 0, ZONE_ID).toInstant();
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Callable<CreditResetResultDto>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final String actorName = i == 0 ? "STAFF_MARSHAL_1" : "SYSTEM_AUTOMATIC_SCHEDULER_" + i;
            tasks.add(() -> {
                readyLatch.countDown();
                startLatch.await();
                return creditResetService.resetWeeklyCredits(mondayWeek43, actorName);
            });
        }

        List<Future<CreditResetResultDto>> futures = new ArrayList<>();
        for (Callable<CreditResetResultDto> task : tasks) {
            futures.add(executor.submit(task));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        int executedCountAcrossThreads = 0;
        int skippedCountAcrossThreads = 0;
        for (Future<CreditResetResultDto> future : futures) {
            CreditResetResultDto res = future.get(10, TimeUnit.SECONDS);
            if (res.executed()) {
                executedCountAcrossThreads++;
            }
            if (res.alreadyExecuted()) {
                skippedCountAcrossThreads++;
            }
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Exactly one thread executed the reset; all other threads safely skipped it
        assertThat(executedCountAcrossThreads).isEqualTo(1);
        assertThat(skippedCountAcrossThreads).isEqualTo(threadCount - 1);

        // Exactly 1 audit record exists in the repository
        assertThat(creditResetExecutionRepository.count()).isEqualTo(1);

        // All active students verified at 6 credits
        Student alice = studentRepository.findById(activeStudent1.getId()).orElseThrow();
        assertThat(alice.getWeeklyCredits()).isEqualTo(6);
    }
}
