package edu.university.badminton.controller;

import edu.university.badminton.dto.SessionConfigResponseDto;
import edu.university.badminton.dto.UpdateSessionConfigRequestDto;
import edu.university.badminton.model.SessionType;
import edu.university.badminton.service.SessionConfigService;
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

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SessionConfigControllerTest {

    @Mock
    private SessionConfigService sessionConfigService;

    @InjectMocks
    private SessionConfigController sessionConfigController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(sessionConfigController).build();
    }

    @Test
    @DisplayName("GET /api/sessions should return empty JSON array when no sessions exist")
    void shouldReturnEmptyArrayWhenNoSessions() throws Exception {
        when(sessionConfigService.getAllSessions()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/sessions")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("GET /api/sessions should return session configurations with queue and timing rules")
    void shouldReturnSessionConfigurations() throws Exception {
        List<SessionConfigResponseDto> sessions = List.of(
                new SessionConfigResponseDto(
                        1L,
                        SessionType.MORNING,
                        LocalTime.of(6, 0),
                        LocalTime.of(8, 30),
                        30,
                        LocalTime.of(21, 0),
                        LocalTime.of(21, 5),
                        1,
                        true
                ),
                new SessionConfigResponseDto(
                        2L,
                        SessionType.EVENING,
                        LocalTime.of(15, 30),
                        LocalTime.of(19, 0),
                        30,
                        LocalTime.of(12, 0),
                        LocalTime.of(12, 5),
                        0,
                        true
                )
        );
        when(sessionConfigService.getAllSessions()).thenReturn(sessions);

        mockMvc.perform(get("/api/sessions")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].sessionType").value("MORNING"))
                .andExpect(jsonPath("$[0].startTime").value("06:00:00"))
                .andExpect(jsonPath("$[0].endTime").value("08:30:00"))
                .andExpect(jsonPath("$[0].slotDurationMinutes").value(30))
                .andExpect(jsonPath("$[0].queueOpenTime").value("21:00:00"))
                .andExpect(jsonPath("$[0].bookingStartTime").value("21:05:00"))
                .andExpect(jsonPath("$[0].queueDaysInAdvance").value(1))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[1].sessionType").value("EVENING"))
                .andExpect(jsonPath("$[1].startTime").value("15:30:00"));
    }

    @Test
    @DisplayName("GET /api/sessions/{id} should return session configuration when found")
    void shouldReturnSessionById() throws Exception {
        SessionConfigResponseDto session = new SessionConfigResponseDto(
                1L,
                SessionType.MORNING,
                LocalTime.of(6, 0),
                LocalTime.of(8, 30),
                30,
                LocalTime.of(21, 0),
                LocalTime.of(21, 5),
                1,
                true
        );
        when(sessionConfigService.getSessionById(1L)).thenReturn(session);

        mockMvc.perform(get("/api/sessions/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sessionType").value("MORNING"))
                .andExpect(jsonPath("$.slotDurationMinutes").value(30));
    }

    @Test
    @DisplayName("GET /api/sessions/{id} should return 404 when session not found")
    void shouldReturn404WhenSessionNotFoundById() throws Exception {
        when(sessionConfigService.getSessionById(999L)).thenThrow(new NoSuchElementException("Session configuration not found with ID: 999"));

        mockMvc.perform(get("/api/sessions/999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Session configuration not found with ID: 999"));
    }

    @Test
    @DisplayName("PUT /api/sessions/{id} should return updated session on success")
    void shouldReturnUpdatedSessionOnPut() throws Exception {
        SessionConfigResponseDto updated = new SessionConfigResponseDto(
                1L,
                SessionType.MORNING,
                LocalTime.of(6, 30),
                LocalTime.of(9, 0),
                45,
                LocalTime.of(20, 0),
                LocalTime.of(20, 10),
                1,
                true
        );
        when(sessionConfigService.updateSessionConfig(eq(1L), any(UpdateSessionConfigRequestDto.class)))
                .thenReturn(updated);

        String jsonPayload = """
                {
                    "startTime": "06:30:00",
                    "endTime": "09:00:00",
                    "slotDurationMinutes": 45,
                    "queueOpenTime": "20:00:00",
                    "bookingStartTime": "20:10:00",
                    "queueDaysInAdvance": 1,
                    "active": true
                }
                """;

        mockMvc.perform(put("/api/sessions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotDurationMinutes").value(45))
                .andExpect(jsonPath("$.startTime").value("06:30:00"));
    }

    @Test
    @DisplayName("PUT /api/sessions/{id} should return 400 on validation error")
    void shouldReturn400OnValidationError() throws Exception {
        when(sessionConfigService.updateSessionConfig(eq(1L), any(UpdateSessionConfigRequestDto.class)))
                .thenThrow(new IllegalArgumentException("Session start time must be strictly before end time."));

        String jsonPayload = """
                {
                    "startTime": "09:00:00",
                    "endTime": "06:00:00"
                }
                """;

        mockMvc.perform(put("/api/sessions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Session start time must be strictly before end time."));
    }

    @Test
    @DisplayName("PATCH /api/sessions/{id} should return updated session on partial update")
    void shouldReturnUpdatedSessionOnPatch() throws Exception {
        SessionConfigResponseDto updated = new SessionConfigResponseDto(
                1L,
                SessionType.MORNING,
                LocalTime.of(6, 0),
                LocalTime.of(8, 30),
                30,
                LocalTime.of(21, 0),
                LocalTime.of(21, 5),
                1,
                false
        );
        when(sessionConfigService.updateSessionConfig(eq(1L), any(UpdateSessionConfigRequestDto.class)))
                .thenReturn(updated);

        String jsonPayload = """
                {
                    "active": false
                }
                """;

        mockMvc.perform(patch("/api/sessions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}
