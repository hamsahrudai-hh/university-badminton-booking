package edu.university.badminton.controller;

import edu.university.badminton.dto.CheckInRequestDto;
import edu.university.badminton.dto.CheckInResponseDto;
import edu.university.badminton.dto.NoShowProcessingResponseDto;
import edu.university.badminton.dto.PenalizedPlayerDto;
import edu.university.badminton.dto.ProcessNoShowsRequestDto;
import edu.university.badminton.service.CheckInService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CheckInControllerTest {

    @Mock
    private CheckInService checkInService;

    @InjectMocks
    private CheckInController checkInController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(checkInController).build();
    }

    @Test
    @DisplayName("POST /api/check-in with valid payload returns HTTP 200 OK and check-in details")
    void shouldReturn200OnValidCheckIn() throws Exception {
        CheckInResponseDto responseDto = new CheckInResponseDto(
                true,
                "Player checked in successfully.",
                false,
                10L,
                20L,
                1L,
                "Hamsa Hrudai",
                "23MIC7333",
                1,
                1L,
                1,
                LocalDateTime.of(2026, 10, 15, 6, 0),
                LocalDateTime.of(2026, 10, 15, 6, 30),
                Instant.now(),
                "STAFF_BOB"
        );

        when(checkInService.checkInPlayer(any(CheckInRequestDto.class), any())).thenReturn(responseDto);

        String json = """
                {
                    "qrToken": "QR_VALID_123",
                    "staffIdentifier": "STAFF_BOB"
                }
                """;

        mockMvc.perform(post("/api/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.alreadyCheckedIn").value(false))
                .andExpect(jsonPath("$.studentName").value("Hamsa Hrudai"))
                .andExpect(jsonPath("$.checkedInBy").value("STAFF_BOB"));
    }

    @Test
    @DisplayName("POST /api/check-in returns HTTP 404 Not Found when token does not exist")
    void shouldReturn404OnUnknownToken() throws Exception {
        when(checkInService.checkInPlayer(any(CheckInRequestDto.class), any()))
                .thenThrow(new NoSuchElementException("Invalid or unknown QR check-in token."));

        String json = """
                {
                    "qrToken": "QR_NOT_FOUND"
                }
                """;

        mockMvc.perform(post("/api/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("Invalid or unknown QR check-in token")));
    }

    @Test
    @DisplayName("POST /api/check-in returns HTTP 409 Conflict when outside grace period")
    void shouldReturn409WhenOutsideGracePeriod() throws Exception {
        when(checkInService.checkInPlayer(any(CheckInRequestDto.class), any()))
                .thenThrow(new IllegalStateException("Check-in grace period expired at 06:10."));

        String json = """
                {
                    "qrToken": "QR_EXPIRED"
                }
                """;

        mockMvc.perform(post("/api/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("Check-in grace period expired")));
    }

    @Test
    @DisplayName("POST /api/check-in returns HTTP 400 Bad Request when request body is empty")
    void shouldReturn400OnEmptyBody() throws Exception {
        mockMvc.perform(post("/api/check-in")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Request body is required")));
    }

    @Test
    @DisplayName("POST /api/check-in/process-no-shows returns HTTP 200 OK and penalty list")
    void shouldReturn200OnProcessNoShows() throws Exception {
        PenalizedPlayerDto playerDto = new PenalizedPlayerDto(
                50L,
                10L,
                2L,
                "Bomba",
                "23MIC9999",
                2,
                1,
                LocalDateTime.of(2026, 10, 15, 6, 0),
                3,
                90.0
        );
        NoShowProcessingResponseDto responseDto = new NoShowProcessingResponseDto(
                1,
                List.of(playerDto),
                "Processed 1 no-show player(s)."
        );

        when(checkInService.processNoShows(any(), any())).thenReturn(responseDto);

        mockMvc.perform(post("/api/check-in/process-no-shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedCount").value(1))
                .andExpect(jsonPath("$.penalizedPlayers", hasSize(1)))
                .andExpect(jsonPath("$.penalizedPlayers[0].studentName").value("Bomba"))
                .andExpect(jsonPath("$.penalizedPlayers[0].forfeitedCredits").value(3))
                .andExpect(jsonPath("$.penalizedPlayers[0].updatedReliabilityRating").value(90.0));
    }
}
