package edu.university.badminton.scheduler;

import edu.university.badminton.dto.NoShowProcessingResponseDto;
import edu.university.badminton.service.CheckInService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoShowSchedulerTest {

    @Mock
    private CheckInService checkInService;

    @InjectMocks
    private NoShowScheduler noShowScheduler;

    @Test
    @DisplayName("runAutomaticNoShowProcessing invokes checkInService with SYSTEM_AUTOMATIC_SCHEDULER actor")
    void shouldInvokeCheckInService() {
        when(checkInService.processNoShows(isNull(), eq("SYSTEM_AUTOMATIC_SCHEDULER")))
                .thenReturn(new NoShowProcessingResponseDto(0, List.of(), "No pending no-shows"));

        noShowScheduler.runAutomaticNoShowProcessing();

        verify(checkInService).processNoShows(isNull(), eq("SYSTEM_AUTOMATIC_SCHEDULER"));
    }

    @Test
    @DisplayName("runAutomaticNoShowProcessing handles exceptions gracefully without propagating")
    void shouldHandleExceptionsGracefully() {
        doThrow(new RuntimeException("Database connectivity failure"))
                .when(checkInService).processNoShows(isNull(), eq("SYSTEM_AUTOMATIC_SCHEDULER"));

        // Should not throw
        noShowScheduler.runAutomaticNoShowProcessing();

        verify(checkInService).processNoShows(isNull(), eq("SYSTEM_AUTOMATIC_SCHEDULER"));
    }
}
