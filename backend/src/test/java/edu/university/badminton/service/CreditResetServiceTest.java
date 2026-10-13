package edu.university.badminton.service;

import edu.university.badminton.dto.CreditResetResultDto;
import edu.university.badminton.model.CreditResetExecution;
import edu.university.badminton.model.Student;
import edu.university.badminton.repository.CreditResetExecutionRepository;
import edu.university.badminton.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditResetServiceTest {

    private static final String TIMEZONE = "Asia/Kolkata";
    private static final ZoneId ZONE_ID = ZoneId.of(TIMEZONE);

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private CreditResetExecutionRepository creditResetExecutionRepository;

    // Fixed instant: Monday, 2026-10-19 00:00:00 IST (Week 43 of 2026)
    private final Instant mondayMidnight = ZonedDateTime.of(2026, 10, 19, 0, 0, 0, 0, ZONE_ID).toInstant();
    private Clock fixedClock;

    private CreditResetService creditResetService;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(mondayMidnight, ZONE_ID);
        creditResetService = new CreditResetService(
                studentRepository,
                creditResetExecutionRepository,
                fixedClock,
                TIMEZONE
        );
    }

    @Test
    @DisplayName("Successfully resets eligible students' weekly credits to 6 and records audit execution")
    void shouldResetEligibleStudentsCreditsToSix() {
        String expectedPeriodKey = "2026-W43";
        when(creditResetExecutionRepository.existsByPeriodKey(expectedPeriodKey)).thenReturn(false);
        when(studentRepository.resetEligibleStudentsWeeklyCredits(Student.DEFAULT_WEEKLY_CREDITS)).thenReturn(7);

        CreditResetResultDto result = creditResetService.resetWeeklyCredits("SYSTEM_AUTOMATIC_SCHEDULER");

        assertThat(result.executed()).isTrue();
        assertThat(result.alreadyExecuted()).isFalse();
        assertThat(result.periodKey()).isEqualTo(expectedPeriodKey);
        assertThat(result.studentsResetCount()).isEqualTo(7);
        assertThat(result.triggeredBy()).isEqualTo("SYSTEM_AUTOMATIC_SCHEDULER");
        assertThat(result.message()).contains("Weekly credits successfully reset to 6 for 7 eligible student(s)");

        verify(studentRepository).resetEligibleStudentsWeeklyCredits(eq(6));

        ArgumentCaptor<CreditResetExecution> captor = ArgumentCaptor.forClass(CreditResetExecution.class);
        verify(creditResetExecutionRepository).save(captor.capture());
        CreditResetExecution saved = captor.getValue();
        assertThat(saved.getPeriodKey()).isEqualTo(expectedPeriodKey);
        assertThat(saved.getStudentsResetCount()).isEqualTo(7);
        assertThat(saved.getTriggeredBy()).isEqualTo("SYSTEM_AUTOMATIC_SCHEDULER");
        assertThat(saved.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("Skips duplicate execution when the weekly period key is already recorded (Restart Safety)")
    void shouldSkipDuplicateExecutionInSameWeek() {
        String expectedPeriodKey = "2026-W43";
        CreditResetExecution existing = new CreditResetExecution(
                expectedPeriodKey,
                mondayMidnight,
                7,
                "SYSTEM_AUTOMATIC_SCHEDULER",
                "SUCCESS",
                "Initial reset"
        );
        when(creditResetExecutionRepository.existsByPeriodKey(expectedPeriodKey)).thenReturn(true);
        when(creditResetExecutionRepository.findByPeriodKey(expectedPeriodKey)).thenReturn(Optional.of(existing));

        // Attempt second reset (e.g. after server restart or duplicate scheduler tick)
        CreditResetResultDto result = creditResetService.resetWeeklyCredits("SYSTEM_AUTOMATIC_SCHEDULER");

        assertThat(result.executed()).isFalse();
        assertThat(result.alreadyExecuted()).isTrue();
        assertThat(result.periodKey()).isEqualTo(expectedPeriodKey);
        assertThat(result.studentsResetCount()).isEqualTo(7);
        assertThat(result.message()).contains("already executed earlier; skipped duplicate run");

        // Must NEVER update student credits again or save another record
        verify(studentRepository, never()).resetEligibleStudentsWeeklyCredits(any(int.class));
        verify(creditResetExecutionRepository, never()).save(any(CreditResetExecution.class));
    }

    @Test
    @DisplayName("Repeated requests in the same week are strictly rejected without any override mechanism")
    void shouldStrictlyEnforcePeriodAuthoritativenessWithoutOverride() {
        String expectedPeriodKey = "2026-W43";
        CreditResetExecution existing = new CreditResetExecution(
                expectedPeriodKey,
                mondayMidnight,
                5,
                "INITIAL_STAFF",
                "SUCCESS",
                "Initial reset"
        );
        when(creditResetExecutionRepository.existsByPeriodKey(expectedPeriodKey)).thenReturn(true);
        when(creditResetExecutionRepository.findByPeriodKey(expectedPeriodKey)).thenReturn(Optional.of(existing));

        // Calling reset again in the same week
        CreditResetResultDto result = creditResetService.resetWeeklyCredits("ANOTHER_STAFF");

        assertThat(result.executed()).isFalse();
        assertThat(result.alreadyExecuted()).isTrue();
        assertThat(result.studentsResetCount()).isEqualTo(5);

        // Deduplication cannot be bypassed
        verify(studentRepository, never()).resetEligibleStudentsWeeklyCredits(any(int.class));
    }

    @Test
    @DisplayName("Handles concurrent race gracefully when unique constraint is violated")
    void shouldHandleConcurrentRaceGracefullyWhenUniqueConstraintViolated() {
        String expectedPeriodKey = "2026-W43";
        when(creditResetExecutionRepository.existsByPeriodKey(expectedPeriodKey)).thenReturn(false);
        when(studentRepository.resetEligibleStudentsWeeklyCredits(6)).thenReturn(6);
        doThrow(new DataIntegrityViolationException("Duplicate entry '2026-W43' for key 'uk_credit_reset_period_key'"))
                .when(creditResetExecutionRepository).save(any(CreditResetExecution.class));

        CreditResetExecution existing = new CreditResetExecution(
                expectedPeriodKey,
                mondayMidnight,
                6,
                "CONCURRENT_RUNNER",
                "SUCCESS",
                "Concurrent win"
        );
        when(creditResetExecutionRepository.findByPeriodKey(expectedPeriodKey)).thenReturn(Optional.of(existing));

        CreditResetResultDto result = creditResetService.resetWeeklyCredits("CONCURRENT_WORKER");

        assertThat(result.executed()).isFalse();
        assertThat(result.alreadyExecuted()).isTrue();
        assertThat(result.periodKey()).isEqualTo(expectedPeriodKey);
    }

    @Test
    @DisplayName("Rolls back cleanly and leaves no execution record if student credit update fails")
    void shouldRollbackAtomicallyWhenCreditUpdateFails() {
        String expectedPeriodKey = "2026-W43";
        when(creditResetExecutionRepository.existsByPeriodKey(expectedPeriodKey)).thenReturn(false);
        when(studentRepository.resetEligibleStudentsWeeklyCredits(6))
                .thenThrow(new RuntimeException("Database connection timeout during credit update"));

        assertThatThrownBy(() -> creditResetService.resetWeeklyCredits("SYSTEM_AUTOMATIC_SCHEDULER"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database connection timeout");

        // CreditResetExecution must NOT be saved when student update fails
        verify(creditResetExecutionRepository, never()).save(any(CreditResetExecution.class));
    }

    @Test
    @DisplayName("Accurately computes ISO-8601 week period key across Kolkata timezone boundaries")
    void shouldComputeCorrectIsoWeekPeriodKeyAcrossTimezoneBoundaries() {
        // Sunday 2026-10-18 23:59:59 IST -> ISO Week 42
        Instant sundayNight = ZonedDateTime.of(2026, 10, 18, 23, 59, 59, 999000000, ZONE_ID).toInstant();
        assertThat(creditResetService.computePeriodKey(sundayNight)).isEqualTo("2026-W42");

        // Monday 2026-10-19 00:00:00 IST -> ISO Week 43
        Instant mondayMidnightInstant = ZonedDateTime.of(2026, 10, 19, 0, 0, 0, 0, ZONE_ID).toInstant();
        assertThat(creditResetService.computePeriodKey(mondayMidnightInstant)).isEqualTo("2026-W43");

        // Wednesday mid-week 2026-10-21 14:30:00 IST -> ISO Week 43
        Instant wednesdayMidWeek = ZonedDateTime.of(2026, 10, 21, 14, 30, 0, 0, ZONE_ID).toInstant();
        assertThat(creditResetService.computePeriodKey(wednesdayMidWeek)).isEqualTo("2026-W43");

        // Sunday end-of-week 2026-10-25 23:59:59 IST -> ISO Week 43
        Instant sundayEndOfWeek = ZonedDateTime.of(2026, 10, 25, 23, 59, 59, 0, ZONE_ID).toInstant();
        assertThat(creditResetService.computePeriodKey(sundayEndOfWeek)).isEqualTo("2026-W43");

        // Next Monday 2026-10-26 00:00:00 IST -> ISO Week 44
        Instant nextMonday = ZonedDateTime.of(2026, 10, 26, 0, 0, 0, 0, ZONE_ID).toInstant();
        assertThat(creditResetService.computePeriodKey(nextMonday)).isEqualTo("2026-W44");
    }

    @Test
    @DisplayName("Returns correct reset status and current week audit execution")
    void shouldRetrieveStatusAndCurrentPeriodKey() {
        String expectedPeriodKey = "2026-W43";
        CreditResetExecution execution = new CreditResetExecution(
                expectedPeriodKey,
                mondayMidnight,
                6,
                "SYSTEM_AUTOMATIC_SCHEDULER",
                "SUCCESS",
                "Audit notes"
        );
        when(creditResetExecutionRepository.existsByPeriodKey(expectedPeriodKey)).thenReturn(true);
        when(creditResetExecutionRepository.findByPeriodKey(expectedPeriodKey)).thenReturn(Optional.of(execution));
        when(creditResetExecutionRepository.findTopByOrderByExecutedAtDesc()).thenReturn(Optional.of(execution));

        assertThat(creditResetService.getCurrentPeriodKey()).isEqualTo(expectedPeriodKey);
        assertThat(creditResetService.isCurrentWeekResetCompleted()).isTrue();
        assertThat(creditResetService.getExecutionForCurrentWeek()).contains(execution);
        assertThat(creditResetService.getLatestExecution()).contains(execution);
    }
}
