package edu.university.badminton.scheduler;

import edu.university.badminton.dto.CreditResetResultDto;
import edu.university.badminton.service.CreditResetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditResetSchedulerTest {

    @Mock
    private CreditResetService creditResetService;

    @InjectMocks
    private WeeklyCreditResetScheduler weeklyCreditResetScheduler;

    @Test
    @DisplayName("runWeeklyCreditReset invokes creditResetService with SYSTEM_AUTOMATIC_SCHEDULER actor")
    void shouldInvokeCreditResetService() {
        when(creditResetService.resetWeeklyCredits(eq("SYSTEM_AUTOMATIC_SCHEDULER")))
                .thenReturn(CreditResetResultDto.success("2026-W42", Instant.now(), 5, "SYSTEM_AUTOMATIC_SCHEDULER"));

        weeklyCreditResetScheduler.runWeeklyCreditReset();

        verify(creditResetService).resetWeeklyCredits(eq("SYSTEM_AUTOMATIC_SCHEDULER"));
    }

    @Test
    @DisplayName("runWeeklyCreditReset handles exceptions gracefully without propagating")
    void shouldHandleExceptionsGracefully() {
        doThrow(new RuntimeException("Database error during reset"))
                .when(creditResetService).resetWeeklyCredits(eq("SYSTEM_AUTOMATIC_SCHEDULER"));

        // Should not throw
        weeklyCreditResetScheduler.runWeeklyCreditReset();

        verify(creditResetService).resetWeeklyCredits(eq("SYSTEM_AUTOMATIC_SCHEDULER"));
    }
}
