package edu.university.badminton.service;

import edu.university.badminton.dto.CreditResetResultDto;
import edu.university.badminton.model.CreditResetExecution;
import edu.university.badminton.model.Student;
import edu.university.badminton.repository.CreditResetExecutionRepository;
import edu.university.badminton.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.util.Optional;

/**
 * Service orchestrating safe, automated, and restart-safe weekly student-credit resets.
 * <p>
 * Guarantees:
 * 1. Resets eligible active students' weekly credits to exactly 6 (never increments).
 * 2. Deduplicates executions per ISO week (Monday-Sunday) in the configured university timezone.
 * 3. Prevents duplicate runs across server restarts or repeated requests from wiping mid-week student bookings.
 * 4. Strictly prohibits unsafe force-reset overrides: the period's unique execution record is authoritative.
 * 5. Atomicity & Concurrency: executes in a single transaction; concurrent requests cannot double-reset or leave partial state.
 */
@Service
public class CreditResetService {

    private static final Logger log = LoggerFactory.getLogger(CreditResetService.class);

    private final StudentRepository studentRepository;
    private final CreditResetExecutionRepository creditResetExecutionRepository;
    private final Clock clock;
    private final ZoneId zoneId;
    private final TransactionTemplate transactionTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    public CreditResetService(
            StudentRepository studentRepository,
            CreditResetExecutionRepository creditResetExecutionRepository,
            Clock clock,
            @Value("${app.timezone:Asia/Kolkata}") String timezone,
            PlatformTransactionManager transactionManager
    ) {
        this.studentRepository = studentRepository;
        this.creditResetExecutionRepository = creditResetExecutionRepository;
        this.clock = clock;
        this.zoneId = ZoneId.of(timezone.trim());
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Package-private test constructor providing a pass-through TransactionTemplate.
     */
    CreditResetService(
            StudentRepository studentRepository,
            CreditResetExecutionRepository creditResetExecutionRepository,
            Clock clock,
            String timezone
    ) {
        this(studentRepository, creditResetExecutionRepository, clock, timezone, new TransactionTemplate(new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
            }
        }));
    }

    /**
     * Package-private constructor allowing explicit TransactionTemplate injection.
     */
    CreditResetService(
            StudentRepository studentRepository,
            CreditResetExecutionRepository creditResetExecutionRepository,
            Clock clock,
            String timezone,
            TransactionTemplate transactionTemplate
    ) {
        this.studentRepository = studentRepository;
        this.creditResetExecutionRepository = creditResetExecutionRepository;
        this.clock = clock;
        this.zoneId = ZoneId.of(timezone.trim());
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Executes the weekly credit reset for the current week period key.
     * Idempotent: strictly skips duplicate execution if the week was already reset.
     * No force override is permitted.
     *
     * @param triggeredBy description of caller or trigger (e.g. "SYSTEM_AUTOMATIC_SCHEDULER" or staff username)
     * @return summary result of the reset operation
     */
    public CreditResetResultDto resetWeeklyCredits(String triggeredBy) {
        return resetWeeklyCredits(clock.instant(), triggeredBy);
    }

    /**
     * Executes the weekly credit reset evaluated at a specific instant (useful for scheduled jobs and testing).
     * Thread-safe and atomic: serializes execution within the JVM and relies on database unique constraints
     * across distributed nodes to guarantee zero duplicate resets or partial updates.
     *
     * @param evaluationInstant specific timestamp to evaluate week period key (defaults to clock.instant() if null)
     * @param triggeredBy       description of caller or trigger
     * @return summary result of the reset operation
     */
    public synchronized CreditResetResultDto resetWeeklyCredits(Instant evaluationInstant, String triggeredBy) {
        Instant now = evaluationInstant != null ? evaluationInstant : clock.instant();
        String periodKey = computePeriodKey(now);

        // Fast check before opening transaction
        if (creditResetExecutionRepository.existsByPeriodKey(periodKey)) {
            log.info("Weekly credit reset for period [{}] has already been executed; skipping duplicate run (triggered by [{}]).",
                    periodKey, triggeredBy);
            return buildSkippedDto(periodKey, triggeredBy, now);
        }

        try {
            return transactionTemplate.execute(status -> {
                // Double-check inside active transaction
                if (creditResetExecutionRepository.existsByPeriodKey(periodKey)) {
                    log.info("Weekly credit reset for period [{}] found completed inside transaction; skipping duplicate run (triggered by [{}]).",
                            periodKey, triggeredBy);
                    return buildSkippedDto(periodKey, triggeredBy, now);
                }

                log.info("Executing weekly credit reset for period [{}] (triggered by [{}]). Target balance: {} credits.",
                        periodKey, triggeredBy, Student.DEFAULT_WEEKLY_CREDITS);

                int updatedCount = studentRepository.resetEligibleStudentsWeeklyCredits(Student.DEFAULT_WEEKLY_CREDITS);

                CreditResetExecution execution = new CreditResetExecution(
                        periodKey,
                        now,
                        updatedCount,
                        triggeredBy,
                        "SUCCESS",
                        "Weekly credit reset to " + Student.DEFAULT_WEEKLY_CREDITS + " credits"
                );

                creditResetExecutionRepository.save(execution);

                log.info("Weekly credit reset completed for period [{}]. Successfully reset {} student(s) to {} credits.",
                        periodKey, updatedCount, Student.DEFAULT_WEEKLY_CREDITS);

                return CreditResetResultDto.success(periodKey, now, updatedCount, triggeredBy);
            });
        } catch (DataIntegrityViolationException e) {
            // Concurrent cluster node or thread committed periodKey concurrently
            log.warn("Concurrent credit reset detected for period [{}] (unique constraint enforced). Returning duplicate-skip result.", periodKey);
            return buildSkippedDto(periodKey, triggeredBy, now);
        }
    }

    private CreditResetResultDto buildSkippedDto(String periodKey, String triggeredBy, Instant now) {
        Optional<CreditResetExecution> existing = creditResetExecutionRepository.findByPeriodKey(periodKey);
        int prevCount = existing.map(CreditResetExecution::getStudentsResetCount).orElse(0);
        Instant executedTime = existing.map(CreditResetExecution::getExecutedAt).orElse(now);
        return CreditResetResultDto.skippedAlreadyExecuted(periodKey, executedTime, prevCount, triggeredBy);
    }

    /**
     * Computes the ISO-8601 week period key (e.g. "2026-W42") in the configured university timezone.
     *
     * @param instant the point in time to evaluate
     * @return formatted period key
     */
    public String computePeriodKey(Instant instant) {
        ZonedDateTime zdt = instant.atZone(zoneId);
        int week = zdt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int year = zdt.get(IsoFields.WEEK_BASED_YEAR);
        return String.format("%d-W%02d", year, week);
    }

    /**
     * Returns the current week's period key based on the configured clock.
     */
    public String getCurrentPeriodKey() {
        return computePeriodKey(clock.instant());
    }

    /**
     * Returns the execution audit record for the current week, if one has occurred.
     */
    public Optional<CreditResetExecution> getExecutionForCurrentWeek() {
        return creditResetExecutionRepository.findByPeriodKey(getCurrentPeriodKey());
    }

    /**
     * Returns the most recent credit reset execution record.
     */
    public Optional<CreditResetExecution> getLatestExecution() {
        return creditResetExecutionRepository.findTopByOrderByExecutedAtDesc();
    }

    /**
     * Checks if the reset for the current week has already been executed.
     */
    public boolean isCurrentWeekResetCompleted() {
        return creditResetExecutionRepository.existsByPeriodKey(getCurrentPeriodKey());
    }

    public ZoneId getZoneId() {
        return zoneId;
    }
}
