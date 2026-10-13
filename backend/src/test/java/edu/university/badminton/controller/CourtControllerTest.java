package edu.university.badminton.controller;

import edu.university.badminton.dto.CourtResponseDto;
import edu.university.badminton.service.CourtService;
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

import java.util.Collections;
import java.util.List;

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
class CourtControllerTest {

    @Mock
    private CourtService courtService;

    @InjectMocks
    private CourtController courtController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(courtController).build();
    }

    @Test
    @DisplayName("GET /api/courts should return empty JSON array when no courts exist")
    void shouldReturnEmptyArrayWhenNoCourts() throws Exception {
        when(courtService.getAllCourts()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/courts")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("GET /api/courts should return courts list DTOs without internal entity details")
    void shouldReturnCourtsList() throws Exception {
        List<CourtResponseDto> courts = List.of(
                new CourtResponseDto(1L, 1, true),
                new CourtResponseDto(2L, 2, true)
        );
        when(courtService.getAllCourts()).thenReturn(courts);

        mockMvc.perform(get("/api/courts")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].courtNumber").value(1))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$[1].courtNumber").value(2));
    }

    @Test
    @DisplayName("GET /api/courts?activeOnly=true should query active courts")
    void shouldReturnActiveCourtsOnly() throws Exception {
        List<CourtResponseDto> courts = List.of(new CourtResponseDto(1L, 1, true));
        when(courtService.getActiveCourts()).thenReturn(courts);

        mockMvc.perform(get("/api/courts?activeOnly=true")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].courtNumber").value(1));
    }

    @Test
    @DisplayName("GET /api/courts/{id} should return court when found")
    void shouldReturnCourtById() throws Exception {
        CourtResponseDto court = new CourtResponseDto(1L, 1, true);
        when(courtService.getCourtById(1L)).thenReturn(court);

        mockMvc.perform(get("/api/courts/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.courtNumber").value(1))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("GET /api/courts/{id} should return 404 when court not found")
    void shouldReturn404WhenCourtNotFound() throws Exception {
        when(courtService.getCourtById(99L))
                .thenThrow(new java.util.NoSuchElementException("Court not found with ID: 99"));

        mockMvc.perform(get("/api/courts/99")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Court not found with ID: 99"));
    }

    @Test
    @DisplayName("PATCH /api/courts/{id}/status should update status successfully")
    void shouldUpdateCourtStatusViaPatch() throws Exception {
        CourtResponseDto updated = new CourtResponseDto(1L, 1, false);
        when(courtService.updateCourtStatus(eq(1L), any(edu.university.badminton.dto.UpdateCourtStatusRequestDto.class)))
                .thenReturn(updated);

        mockMvc.perform(patch("/api/courts/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("PATCH /api/courts/{id}/status should return 404 for invalid court ID")
    void shouldReturn404OnPatchWhenCourtNotFound() throws Exception {
        when(courtService.updateCourtStatus(eq(99L), any()))
                .thenThrow(new java.util.NoSuchElementException("Court not found with ID: 99"));

        mockMvc.perform(patch("/api/courts/99/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Court not found with ID: 99"));
    }

    @Test
    @DisplayName("PATCH /api/courts/{id}/status should return 409 Conflict on invalid state transition")
    void shouldReturn409OnDuplicateStatus() throws Exception {
        when(courtService.updateCourtStatus(eq(1L), any()))
                .thenThrow(new IllegalStateException("Court 1 is already active."));

        mockMvc.perform(patch("/api/courts/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Court 1 is already active."));
    }

    @Test
    @DisplayName("PATCH /api/courts/{id}/status should return 400 Bad Request on invalid payload")
    void shouldReturn400OnInvalidPayload() throws Exception {
        when(courtService.updateCourtStatus(eq(1L), any()))
                .thenThrow(new IllegalArgumentException("The 'active' status field is required."));

        mockMvc.perform(patch("/api/courts/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The 'active' status field is required."));
    }

    @Test
    @DisplayName("PUT /api/courts/{id}/status should update status successfully")
    void shouldUpdateCourtStatusViaPut() throws Exception {
        CourtResponseDto updated = new CourtResponseDto(2L, 2, true);
        when(courtService.updateCourtStatus(eq(2L), any(edu.university.badminton.dto.UpdateCourtStatusRequestDto.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/courts/2/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.active").value(true));
    }
}
