package edu.university.badminton.controller;

import edu.university.badminton.dto.SlotGenerationResponseDto;
import edu.university.badminton.dto.TimeSlotResponseDto;
import edu.university.badminton.model.SessionType;
import edu.university.badminton.model.SlotStatus;
import edu.university.badminton.service.TimeSlotService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TimeSlotControllerTest {

    @Mock
    private TimeSlotService timeSlotService;

    @InjectMocks
    private TimeSlotController timeSlotController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(timeSlotController).build();
    }

    // --- GET /api/slots tests ---

    @Test
    @DisplayName("GET /api/slots without date parameter should return HTTP 400 Bad Request")
    void shouldReturnBadRequestWhenDateIsMissing() throws Exception {
        mockMvc.perform(get("/api/slots")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("The 'date' query parameter is required")));
    }

    @Test
    @DisplayName("GET /api/slots with blank date should return HTTP 400 Bad Request")
    void shouldReturnBadRequestWhenDateIsBlank() throws Exception {
        mockMvc.perform(get("/api/slots")
                        .param("date", "   ")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("The 'date' query parameter is required")));
    }

    @Test
    @DisplayName("GET /api/slots with malformed date should return HTTP 400 Bad Request")
    void shouldReturnBadRequestWhenDateIsInvalid() throws Exception {
        mockMvc.perform(get("/api/slots")
                        .param("date", "2026-99-99")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Invalid date format")));
    }

    @Test
    @DisplayName("GET /api/slots with non-date string should return HTTP 400 Bad Request")
    void shouldReturnBadRequestWhenDateIsNotADate() throws Exception {
        mockMvc.perform(get("/api/slots")
                        .param("date", "not-a-date")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Invalid date format")));
    }

    @Test
    @DisplayName("GET /api/slots?date=YYYY-MM-DD should return empty array when no slots exist")
    void shouldReturnEmptyArrayWhenNoSlots() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 15);
        when(timeSlotService.getSlotsByDate(date)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/slots")
                        .param("date", "2026-10-15")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("GET /api/slots?date=YYYY-MM-DD should return slots with court number and status")
    void shouldReturnSlotsForDate() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 15);
        List<TimeSlotResponseDto> slots = List.of(
                new TimeSlotResponseDto(
                        10L,
                        1L,
                        1,
                        SessionType.MORNING,
                        date,
                        LocalDateTime.of(date, LocalTime.of(6, 0)),
                        LocalDateTime.of(date, LocalTime.of(6, 30)),
                        SlotStatus.AVAILABLE
                ),
                new TimeSlotResponseDto(
                        11L,
                        2L,
                        2,
                        SessionType.MORNING,
                        date,
                        LocalDateTime.of(date, LocalTime.of(6, 0)),
                        LocalDateTime.of(date, LocalTime.of(6, 30)),
                        SlotStatus.BLOCKED_TOURNAMENT
                )
        );
        when(timeSlotService.getSlotsByDate(date)).thenReturn(slots);

        mockMvc.perform(get("/api/slots")
                        .param("date", "2026-10-15")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].courtNumber").value(1))
                .andExpect(jsonPath("$[0].sessionType").value("MORNING"))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$[1].courtNumber").value(2))
                .andExpect(jsonPath("$[1].status").value("BLOCKED_TOURNAMENT"));
    }

    // --- POST /api/slots/generate tests ---

    @Test
    @DisplayName("POST /api/slots/generate without date should return HTTP 400 Bad Request")
    void shouldReturnBadRequestWhenGenerateDateIsMissing() throws Exception {
        mockMvc.perform(post("/api/slots/generate")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("The 'date' parameter is required")));
    }

    @Test
    @DisplayName("POST /api/slots/generate with invalid date should return HTTP 400 Bad Request")
    void shouldReturnBadRequestWhenGenerateDateIsInvalid() throws Exception {
        mockMvc.perform(post("/api/slots/generate")
                        .param("date", "invalid-date")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Invalid date format")));
    }

    @Test
    @DisplayName("POST /api/slots/generate?date=YYYY-MM-DD should return HTTP 201 when new slots are created")
    void shouldReturn201WhenSlotsCreated() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 15);
        SlotGenerationResponseDto responseDto = new SlotGenerationResponseDto(
                date,
                48,
                48,
                0,
                "Successfully generated 48 time slots for 2026-10-15 across 4 courts (0 pre-existing slots preserved)."
        );
        when(timeSlotService.generateSlotsForDate(date)).thenReturn(responseDto);

        mockMvc.perform(post("/api/slots/generate")
                        .param("date", "2026-10-15")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.date").value("2026-10-15"))
                .andExpect(jsonPath("$.totalSlots").value(48))
                .andExpect(jsonPath("$.createdSlots").value(48))
                .andExpect(jsonPath("$.existingSlots").value(0))
                .andExpect(jsonPath("$.message", containsString("Successfully generated 48 time slots")));
    }

    @Test
    @DisplayName("POST /api/slots/generate?date=YYYY-MM-DD should return HTTP 200 when slots already exist")
    void shouldReturn200WhenSlotsAlreadyExist() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 15);
        SlotGenerationResponseDto responseDto = new SlotGenerationResponseDto(
                date,
                48,
                0,
                48,
                "Slots already exist for 2026-10-15. 0 new slots created, 48 existing slots preserved."
        );
        when(timeSlotService.generateSlotsForDate(date)).thenReturn(responseDto);

        mockMvc.perform(post("/api/slots/generate")
                        .param("date", "2026-10-15")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdSlots").value(0))
                .andExpect(jsonPath("$.existingSlots").value(48));
    }

    @Test
    @DisplayName("POST /api/slots/generate with JSON body should return HTTP 201")
    void shouldAcceptDateInJsonBody() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 15);
        SlotGenerationResponseDto responseDto = new SlotGenerationResponseDto(
                date,
                48,
                48,
                0,
                "Successfully generated 48 time slots for 2026-10-15 across 4 courts."
        );
        when(timeSlotService.generateSlotsForDate(date)).thenReturn(responseDto);

        mockMvc.perform(post("/api/slots/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-10-15\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalSlots").value(48));
    }

    @Test
    @DisplayName("POST /api/slots/generate should return HTTP 409 Conflict when configuration is missing")
    void shouldReturnConflictWhenNoActiveCourtsOrSessions() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 15);
        when(timeSlotService.generateSlotsForDate(date))
                .thenThrow(new IllegalStateException("No active courts found."));

        mockMvc.perform(post("/api/slots/generate")
                        .param("date", "2026-10-15")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("No active courts found.")));
    }
}
