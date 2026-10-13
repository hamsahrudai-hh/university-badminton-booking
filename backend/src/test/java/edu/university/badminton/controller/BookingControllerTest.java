package edu.university.badminton.controller;

import edu.university.badminton.dto.BookingPlayerResponseDto;
import edu.university.badminton.dto.BookingResponseDto;
import edu.university.badminton.dto.CreateBookingRequestDto;
import edu.university.badminton.model.BookingStatus;
import edu.university.badminton.service.BookingService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private BookingController bookingController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(bookingController).build();
    }

    @Test
    @DisplayName("POST /api/bookings with valid payload returns HTTP 201 Created and booking details")
    void shouldReturn201OnSuccessfulBooking() throws Exception {
        BookingResponseDto responseDto = new BookingResponseDto(
                55L,
                100L,
                1,
                LocalDate.of(2026, 10, 15),
                LocalDateTime.of(2026, 10, 15, 6, 0),
                LocalDateTime.of(2026, 10, 15, 6, 30),
                1L,
                "Alice Smith",
                BookingStatus.CONFIRMED,
                1,
                List.of(
                        new BookingPlayerResponseDto(1L, "Alice Smith", "2024BCS001", 1, "QR_111", false, 1, 5),
                        new BookingPlayerResponseDto(2L, "Bob Jones", "2024BCS002", 2, "QR_222", false, 1, 5)
                ),
                Instant.now()
        );

        when(bookingService.confirmBooking(any(CreateBookingRequestDto.class))).thenReturn(responseDto);

        String jsonBody = """
                {
                    "bookingStudentId": 1,
                    "timeSlotId": 100,
                    "players": [
                        {"studentId": 1, "spotNumber": 1},
                        {"studentId": 2, "spotNumber": 2}
                    ],
                    "idempotencyKey": "test-key-1"
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").value(55))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.creditsDeductedPerPlayer").value(1))
                .andExpect(jsonPath("$.players", hasSize(2)))
                .andExpect(jsonPath("$.players[0].remainingWeeklyCredits").value(5));
    }

    @Test
    @DisplayName("POST /api/bookings with missing body returns HTTP 400 Bad Request")
    void shouldReturn400OnMissingBody() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Request body is required")));
    }

    @Test
    @DisplayName("POST /api/bookings with invalid group size returns HTTP 400 Bad Request")
    void shouldReturn400OnInvalidGroupSize() throws Exception {
        when(bookingService.confirmBooking(any()))
                .thenThrow(new IllegalArgumentException("A booking group must contain between 2 and 4 registered students."));

        String jsonBody = """
                {
                    "bookingStudentId": 1,
                    "timeSlotId": 100,
                    "players": [{"studentId": 1, "spotNumber": 1}]
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("between 2 and 4")));
    }

    @Test
    @DisplayName("POST /api/bookings when student has 0 credits returns HTTP 409 Conflict")
    void shouldReturn409WhenCreditsInsufficient() throws Exception {
        when(bookingService.confirmBooking(any()))
                .thenThrow(new IllegalStateException("Student Bob (ID 2) has insufficient weekly credits (0 credits remaining)."));

        String jsonBody = """
                {
                    "bookingStudentId": 1,
                    "timeSlotId": 100,
                    "players": [
                        {"studentId": 1, "spotNumber": 1},
                        {"studentId": 2, "spotNumber": 2}
                    ]
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("insufficient weekly credits")));
    }

    @Test
    @DisplayName("GET /api/bookings/{id} when found returns HTTP 200 OK")
    void shouldReturn200OnExistingBooking() throws Exception {
        BookingResponseDto responseDto = new BookingResponseDto(
                55L, 100L, 1, LocalDate.of(2026, 10, 15),
                LocalDateTime.of(2026, 10, 15, 6, 0),
                LocalDateTime.of(2026, 10, 15, 6, 30),
                1L, "Alice Smith", BookingStatus.CONFIRMED, 1, List.of(), Instant.now()
        );

        when(bookingService.getBookingById(55L)).thenReturn(responseDto);

        mockMvc.perform(get("/api/bookings/55")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(55));
    }

    @Test
    @DisplayName("GET /api/bookings/{id} when not found returns HTTP 404 Not Found")
    void shouldReturn404OnMissingBooking() throws Exception {
        when(bookingService.getBookingById(999L))
                .thenThrow(new NoSuchElementException("Booking not found with ID: 999"));

        mockMvc.perform(get("/api/bookings/999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("not found")));
    }
}
