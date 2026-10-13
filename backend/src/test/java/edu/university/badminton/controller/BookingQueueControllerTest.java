package edu.university.badminton.controller;

import edu.university.badminton.dto.AdmitNextResponseDto;
import edu.university.badminton.dto.JoinQueueRequestDto;
import edu.university.badminton.dto.QueueEntryResponseDto;
import edu.university.badminton.dto.QueueStatusResponseDto;
import edu.university.badminton.model.QueueEntryStatus;
import edu.university.badminton.model.SessionType;
import edu.university.badminton.service.BookingQueueService;
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
import java.util.NoSuchElementException;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BookingQueueControllerTest {

    @Mock
    private BookingQueueService bookingQueueService;

    @InjectMocks
    private BookingQueueController bookingQueueController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(bookingQueueController).build();
    }

    @Test
    @DisplayName("POST /api/queue/join - should return 201 Created when request is valid")
    void shouldJoinQueueSuccessfully() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 15);
        QueueEntryResponseDto responseDto = new QueueEntryResponseDto(
                501L, 100L, 1L, "Alice Student", date, SessionType.MORNING,
                QueueEntryStatus.WAITING, 1L, Instant.now(), null, null, null,
                "Successfully joined the MORNING queue for 2026-10-15 at position 1."
        );

        when(bookingQueueService.joinQueue(any(JoinQueueRequestDto.class))).thenReturn(responseDto);

        mockMvc.perform(post("/api/queue/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\": 1, \"date\": \"2026-10-15\", \"sessionType\": \"MORNING\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.queueEntryId").value(501L))
                .andExpect(jsonPath("$.studentId").value(1L))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.positionInLine").value(1L));
    }

    @Test
    @DisplayName("POST /api/queue/join - should return 400 Bad Request when studentId is missing")
    void shouldReturnBadRequestWhenStudentIdIsMissing() throws Exception {
        mockMvc.perform(post("/api/queue/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\": \"2026-10-15\", \"sessionType\": \"MORNING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("studentId")));
    }

    @Test
    @DisplayName("POST /api/queue/join - should return 404 Not Found when student does not exist")
    void shouldReturnNotFoundWhenStudentDoesNotExist() throws Exception {
        when(bookingQueueService.joinQueue(any(JoinQueueRequestDto.class)))
                .thenThrow(new NoSuchElementException("Student with ID 999 not found."));

        mockMvc.perform(post("/api/queue/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\": 999, \"date\": \"2026-10-15\", \"sessionType\": \"MORNING\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("Student with ID 999 not found")));
    }

    @Test
    @DisplayName("POST /api/queue/join - should return 403 Forbidden when student is inactive")
    void shouldReturnForbiddenWhenStudentIsInactive() throws Exception {
        when(bookingQueueService.joinQueue(any(JoinQueueRequestDto.class)))
                .thenThrow(new IllegalStateException("Student account is inactive."));

        mockMvc.perform(post("/api/queue/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\": 4, \"date\": \"2026-10-15\", \"sessionType\": \"MORNING\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", containsString("inactive")));
    }

    @Test
    @DisplayName("POST /api/queue/join - should return 409 Conflict when student is already in queue")
    void shouldReturnConflictWhenStudentAlreadyInQueue() throws Exception {
        when(bookingQueueService.joinQueue(any(JoinQueueRequestDto.class)))
                .thenThrow(new IllegalStateException("Student is already in this booking queue."));

        mockMvc.perform(post("/api/queue/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\": 1, \"date\": \"2026-10-15\", \"sessionType\": \"MORNING\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("already in this booking queue")));
    }

    @Test
    @DisplayName("POST /api/queue/admit-next - should return 200 OK with admitted student info")
    void shouldAdmitNextStudentSuccessfully() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 15);
        Instant now = Instant.now();
        AdmitNextResponseDto responseDto = new AdmitNextResponseDto(
                100L, date, SessionType.MORNING, true,
                501L, 1L, "Alice Student", now, now.plusSeconds(60), 60L, 0L,
                "Student Alice Student admitted to 60-second booking opportunity."
        );

        when(bookingQueueService.admitNextStudent(eq(date), eq(SessionType.MORNING))).thenReturn(responseDto);

        mockMvc.perform(post("/api/queue/admit-next")
                        .param("date", "2026-10-15")
                        .param("sessionType", "MORNING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentAdmitted").value(true))
                .andExpect(jsonPath("$.studentId").value(1L))
                .andExpect(jsonPath("$.studentName").value("Alice Student"))
                .andExpect(jsonPath("$.remainingSeconds").value(60L));
    }

    @Test
    @DisplayName("GET /api/queue/status - should return 200 OK with queue status without exposing student identity")
    void shouldReturnQueueStatus() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 15);
        QueueStatusResponseDto responseDto = new QueueStatusResponseDto(
                100L, date, SessionType.MORNING, "OPEN", 5L, 4L, true,
                Instant.now(), Instant.now().plusSeconds(30), 30L,
                "Active booking opportunity in progress (30 seconds remaining)."
        );

        when(bookingQueueService.getQueueStatus(eq(date), eq(SessionType.MORNING))).thenReturn(responseDto);

        mockMvc.perform(get("/api/queue/status")
                        .param("date", "2026-10-15")
                        .param("sessionType", "MORNING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueStatus").value("OPEN"))
                .andExpect(jsonPath("$.totalEntries").value(5L))
                .andExpect(jsonPath("$.waitingCount").value(4L))
                .andExpect(jsonPath("$.hasActiveOpportunity").value(true))
                .andExpect(jsonPath("$.remainingSeconds").value(30L));
    }

    @Test
    @DisplayName("GET /api/queue/position - should return 200 OK with student position")
    void shouldReturnStudentPosition() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 15);
        QueueEntryResponseDto responseDto = new QueueEntryResponseDto(
                502L, 100L, 2L, "Bob Student", date, SessionType.MORNING,
                QueueEntryStatus.WAITING, 2L, Instant.now(), null, null, null,
                "Waiting in queue at position 2."
        );

        when(bookingQueueService.getStudentPosition(eq(2L), eq(date), eq(SessionType.MORNING))).thenReturn(responseDto);

        mockMvc.perform(get("/api/queue/position")
                        .param("studentId", "2")
                        .param("date", "2026-10-15")
                        .param("sessionType", "MORNING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(2L))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.positionInLine").value(2L));
    }
}
