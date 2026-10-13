package edu.university.badminton.controller;

import edu.university.badminton.dto.CreditResetResultDto;
import edu.university.badminton.model.CreditResetExecution;
import edu.university.badminton.service.CreditResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CreditResetControllerTest {

    @Mock
    private CreditResetService creditResetService;

    @InjectMocks
    private CreditResetController creditResetController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(creditResetController).build();
    }

    @Test
    @DisplayName("POST /api/credits/reset-weekly returns HTTP 200 and reset summary")
    void shouldTriggerWeeklyCreditReset() throws Exception {
        Instant now = Instant.parse("2026-10-19T00:00:00Z");
        CreditResetResultDto result = CreditResetResultDto.success("2026-W43", now, 6, "MANUAL_TRIGGER");

        when(creditResetService.resetWeeklyCredits(anyString())).thenReturn(result);

        mockMvc.perform(post("/api/credits/reset-weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodKey").value("2026-W43"))
                .andExpect(jsonPath("$.studentsResetCount").value(6))
                .andExpect(jsonPath("$.executed").value(true))
                .andExpect(jsonPath("$.alreadyExecuted").value(false))
                .andExpect(jsonPath("$.triggeredBy").value("MANUAL_TRIGGER"));

        verify(creditResetService).resetWeeklyCredits("MANUAL_TRIGGER");
    }

    @Test
    @DisplayName("POST /api/credits/reset-weekly returns skipped status on repeat request without overriding")
    void shouldReturnSkippedStatusOnRepeatRequest() throws Exception {
        Instant now = Instant.parse("2026-10-19T00:00:00Z");
        CreditResetResultDto result = CreditResetResultDto.skippedAlreadyExecuted("2026-W43", now, 6, "MANUAL_TRIGGER");

        when(creditResetService.resetWeeklyCredits(anyString())).thenReturn(result);

        mockMvc.perform(post("/api/credits/reset-weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodKey").value("2026-W43"))
                .andExpect(jsonPath("$.executed").value(false))
                .andExpect(jsonPath("$.alreadyExecuted").value(true));

        verify(creditResetService).resetWeeklyCredits("MANUAL_TRIGGER");
    }

    @Test
    @DisplayName("POST /api/credits/reset-weekly ignores any force parameter (unsafe override removed)")
    void shouldIgnoreUnsafeForceParameter() throws Exception {
        Instant now = Instant.parse("2026-10-19T00:00:00Z");
        CreditResetResultDto result = CreditResetResultDto.skippedAlreadyExecuted("2026-W43", now, 6, "MANUAL_TRIGGER");

        when(creditResetService.resetWeeklyCredits(anyString())).thenReturn(result);

        // Attempting to send ?force=true must NOT trigger a forced reset; it simply delegates to authoritative resetWeeklyCredits
        mockMvc.perform(post("/api/credits/reset-weekly?force=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodKey").value("2026-W43"))
                .andExpect(jsonPath("$.executed").value(false))
                .andExpect(jsonPath("$.alreadyExecuted").value(true));

        verify(creditResetService).resetWeeklyCredits("MANUAL_TRIGGER");
    }

    @Test
    @DisplayName("GET /api/credits/reset-status returns current week status and timezone")
    void shouldReturnResetStatus() throws Exception {
        Instant now = Instant.parse("2026-10-19T00:00:00Z");
        CreditResetExecution execution = new CreditResetExecution(
                "2026-W43",
                now,
                6,
                "SYSTEM_AUTOMATIC_SCHEDULER",
                "SUCCESS",
                "Notes"
        );

        when(creditResetService.getCurrentPeriodKey()).thenReturn("2026-W43");
        when(creditResetService.isCurrentWeekResetCompleted()).thenReturn(true);
        when(creditResetService.getZoneId()).thenReturn(ZoneId.of("Asia/Kolkata"));
        when(creditResetService.getExecutionForCurrentWeek()).thenReturn(Optional.of(execution));
        when(creditResetService.getLatestExecution()).thenReturn(Optional.of(execution));

        mockMvc.perform(get("/api/credits/reset-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPeriodKey").value("2026-W43"))
                .andExpect(jsonPath("$.currentWeekResetExecuted").value(true))
                .andExpect(jsonPath("$.universityTimezone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.currentWeekExecution.periodKey").value("2026-W43"))
                .andExpect(jsonPath("$.latestExecution.studentsResetCount").value(6));
    }
}
