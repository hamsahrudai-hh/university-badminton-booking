package edu.university.badminton.controller;

import edu.university.badminton.config.JwtService;
import edu.university.badminton.dto.AdmitNextResponseDto;
import edu.university.badminton.dto.BookingPlayerAssignmentDto;
import edu.university.badminton.dto.BookingPlayerResponseDto;
import edu.university.badminton.dto.BookingResponseDto;
import edu.university.badminton.dto.CheckInRequestDto;
import edu.university.badminton.dto.CheckInResponseDto;
import edu.university.badminton.dto.NoShowProcessingResponseDto;
import edu.university.badminton.dto.QueueEntryResponseDto;
import edu.university.badminton.dto.SlotGenerationResponseDto;
import edu.university.badminton.dto.StudentResponseDto;
import edu.university.badminton.model.BookingStatus;
import edu.university.badminton.model.QueueEntryStatus;
import edu.university.badminton.model.Role;
import edu.university.badminton.model.SessionType;
import edu.university.badminton.model.Student;
import edu.university.badminton.repository.StudentRepository;
import edu.university.badminton.dto.CourtResponseDto;
import edu.university.badminton.dto.CreditResetResultDto;
import edu.university.badminton.dto.SessionConfigResponseDto;
import edu.university.badminton.service.BookingQueueService;
import edu.university.badminton.service.BookingService;
import edu.university.badminton.service.CheckInService;
import edu.university.badminton.service.CourtService;
import edu.university.badminton.service.CreditResetService;
import edu.university.badminton.service.SessionConfigService;
import edu.university.badminton.service.StudentService;
import edu.university.badminton.service.TimeSlotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private StudentRepository studentRepository;

    @MockitoBean
    private CheckInService checkInService;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private BookingQueueService bookingQueueService;

    @MockitoBean
    private StudentService studentService;

    @MockitoBean
    private TimeSlotService timeSlotService;

    @MockitoBean
    private CreditResetService creditResetService;

    @MockitoBean
    private CourtService courtService;

    @MockitoBean
    private SessionConfigService sessionConfigService;

    private Student aliceStudent;
    private Student bobStaff;
    private Student adminUser;
    private Student charlieStudent;
    private Student deactivatedStudent;

    private String studentToken;
    private String staffToken;
    private String adminToken;
    private String secondStudentToken;
    private String deactivatedUserToken;

    @BeforeEach
    void setUp() {
        studentRepository.deleteAll();

        aliceStudent = new Student("Alice Student", "alice@vitapstudent.ac.in", "23MIC1001", Role.STUDENT);
        aliceStudent = studentRepository.save(aliceStudent);
        studentToken = jwtService.generateToken(aliceStudent);

        bobStaff = new Student("Bob Marshal", "bob.staff@vitapstudent.ac.in", "23STAFF01", Role.STAFF);
        bobStaff = studentRepository.save(bobStaff);
        staffToken = jwtService.generateToken(bobStaff);

        adminUser = new Student("Admin User", "admin@vitapstudent.ac.in", "23ADMIN01", Role.ADMIN);
        adminUser = studentRepository.save(adminUser);
        adminToken = jwtService.generateToken(adminUser);

        charlieStudent = new Student("Charlie Teammate", "charlie@vitapstudent.ac.in", "23MIC2002", Role.STUDENT);
        charlieStudent = studentRepository.save(charlieStudent);
        secondStudentToken = jwtService.generateToken(charlieStudent);

        deactivatedStudent = new Student("Deactivated User", "deactivated@vitapstudent.ac.in", "23MIC9999", Role.STUDENT);
        deactivatedStudent.setActive(false);
        deactivatedStudent = studentRepository.save(deactivatedStudent);
        deactivatedUserToken = jwtService.generateToken(deactivatedStudent);
    }

    // ==========================================
    // 1. Public Endpoints & Unauthenticated Checks
    // ==========================================

    @Test
    @DisplayName("Public endpoints like GET /api/health remain accessible without authentication")
    void shouldAllowUnauthenticatedAccessToPublicEndpoints() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Public read-only endpoints like GET /api/courts, /sessions, /slots remain accessible")
    void shouldAllowUnauthenticatedAccessToPublicReadEndpoints() throws Exception {
        mockMvc.perform(get("/api/courts"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/sessions"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/slots?date=2026-10-15"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/students"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/students omits private student fields from public teammate list")
    void shouldOmitPrivateFieldsFromPublicTeammateList() throws Exception {
        when(studentService.getAllTeammateCandidates()).thenReturn(List.of(
                new edu.university.badminton.dto.TeammateResponseDto(1L, "Alice Teammate", true)
        ));

        mockMvc.perform(get("/api/students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].fullName").value("Alice Teammate"))
                .andExpect(jsonPath("$[0].eligible").value(true))
                .andExpect(jsonPath("$[0].universityEmail").doesNotExist())
                .andExpect(jsonPath("$[0].admissionNumber").doesNotExist())
                .andExpect(jsonPath("$[0].weeklyCredits").doesNotExist())
                .andExpect(jsonPath("$[0].reliabilityRating").doesNotExist())
                .andExpect(jsonPath("$[0].role").doesNotExist())
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    @DisplayName("Deactivated account is rejected with HTTP 401 even when presenting unexpired token")
    void shouldRejectDeactivatedAccountEvenWithUnexpiredToken() throws Exception {
        mockMvc.perform(get("/api/students/" + deactivatedStudent.getId())
                        .header("Authorization", "Bearer " + deactivatedUserToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    // ==========================================
    // 2. Student Profile Ownership & Inspection
    // ==========================================

    @Test
    @DisplayName("GET /api/students/{id} without authentication is rejected with HTTP 401 Unauthorized")
    void shouldRejectUnauthenticatedStudentProfile() throws Exception {
        mockMvc.perform(get("/api/students/" + aliceStudent.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("GET /api/students/{id} allows student to inspect their own profile")
    void shouldAllowStudentToInspectOwnProfile() throws Exception {
        StudentResponseDto dto = new StudentResponseDto(
                aliceStudent.getId(), aliceStudent.getFullName(), aliceStudent.getUniversityEmail(),
                aliceStudent.getAdmissionNumber(), aliceStudent.getRole(), 6, 100.0, true
        );
        when(studentService.getStudentById(aliceStudent.getId())).thenReturn(dto);

        mockMvc.perform(get("/api/students/" + aliceStudent.getId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Alice Student"));
    }

    @Test
    @DisplayName("GET /api/students/{id} rejects student inspecting another student's profile with HTTP 403 Forbidden")
    void shouldRejectStudentInspectingAnotherStudentProfile() throws Exception {
        mockMvc.perform(get("/api/students/" + charlieStudent.getId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied: You are not authorized to view another student's profile."));
    }

    @Test
    @DisplayName("GET /api/students/{id} allows STAFF or ADMIN to inspect any student's profile")
    void shouldAllowStaffOrAdminToInspectAnyStudentProfile() throws Exception {
        StudentResponseDto dto = new StudentResponseDto(
                charlieStudent.getId(), charlieStudent.getFullName(), charlieStudent.getUniversityEmail(),
                charlieStudent.getAdmissionNumber(), charlieStudent.getRole(), 6, 100.0, true
        );
        when(studentService.getStudentById(charlieStudent.getId())).thenReturn(dto);

        mockMvc.perform(get("/api/students/" + charlieStudent.getId())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Charlie Teammate"));
    }

    // ==========================================
    // 3. Booking Creation & Ownership
    // ==========================================

    @Test
    @DisplayName("POST /api/bookings without authentication is rejected with HTTP 401 Unauthorized")
    void shouldRejectUnauthenticatedBookingCreation() throws Exception {
        String json = """
                {
                    "bookingStudentId": 1,
                    "timeSlotId": 10,
                    "players": [{"studentId": 1, "spotNumber": 1}, {"studentId": 2, "spotNumber": 2}]
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("POST /api/bookings rejects student attempting to book on behalf of another student with HTTP 403 Forbidden")
    void shouldRejectBookingCreationOnBehalfOfAnotherStudent() throws Exception {
        // Alice attempts to forge booking with Charlie as host
        String json = String.format("""
                {
                    "bookingStudentId": %d,
                    "timeSlotId": 10,
                    "players": [
                        {"studentId": %d, "spotNumber": 1},
                        {"studentId": %d, "spotNumber": 2}
                    ]
                }
                """, charlieStudent.getId(), charlieStudent.getId(), aliceStudent.getId());

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied: You cannot create a booking on behalf of another student."));
    }

    @Test
    @DisplayName("POST /api/bookings allows group booking with legitimate registered teammates when authenticated student is host")
    void shouldAllowGroupBookingWithRegisteredTeammates() throws Exception {
        BookingResponseDto responseDto = new BookingResponseDto(
                55L, 10L, 1, LocalDate.of(2026, 10, 15),
                LocalDateTime.of(2026, 10, 15, 6, 0),
                LocalDateTime.of(2026, 10, 15, 6, 30),
                aliceStudent.getId(), "Alice Student", BookingStatus.CONFIRMED,
                1,
                List.of(
                        new BookingPlayerResponseDto(aliceStudent.getId(), "Alice Student", "23MIC1001", 1, "QR_ALICE", false, 1, 5),
                        new BookingPlayerResponseDto(charlieStudent.getId(), "Charlie Teammate", "23MIC2002", 2, "QR_CHARLIE", false, 1, 5)
                ),
                Instant.now()
        );

        when(bookingService.confirmBooking(any())).thenReturn(responseDto);

        String json = String.format("""
                {
                    "bookingStudentId": %d,
                    "timeSlotId": 10,
                    "players": [
                        {"studentId": %d, "spotNumber": 1},
                        {"studentId": %d, "spotNumber": 2}
                    ]
                }
                """, aliceStudent.getId(), aliceStudent.getId(), charlieStudent.getId());

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").value(55))
                .andExpect(jsonPath("$.bookingStudentName").value("Alice Student"));
    }

    @Test
    @DisplayName("POST /api/bookings rejects group booking if authenticated host is omitted from players list")
    void shouldRejectBookingIfHostIsOmittedFromPlayerGroup() throws Exception {
        // Alice authenticated, but players list has Charlie and Bob without Alice
        String json = String.format("""
                {
                    "bookingStudentId": %d,
                    "timeSlotId": 10,
                    "players": [
                        {"studentId": %d, "spotNumber": 1},
                        {"studentId": %d, "spotNumber": 2}
                    ]
                }
                """, aliceStudent.getId(), charlieStudent.getId(), bobStaff.getId());

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The authenticated booking host must be included as one of the group players."));
    }

    // ==========================================
    // 4. Booking Inspection & Participant Privacy
    // ==========================================

    @Test
    @DisplayName("GET /api/bookings/{id} without authentication is rejected with HTTP 401 Unauthorized")
    void shouldRejectUnauthenticatedBookingInspection() throws Exception {
        mockMvc.perform(get("/api/bookings/55"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("GET /api/bookings/{id} permits participating host and teammate students to inspect booking")
    void shouldAllowBookingParticipantsToInspectBooking() throws Exception {
        BookingResponseDto responseDto = new BookingResponseDto(
                55L, 10L, 1, LocalDate.of(2026, 10, 15),
                LocalDateTime.of(2026, 10, 15, 6, 0),
                LocalDateTime.of(2026, 10, 15, 6, 30),
                aliceStudent.getId(), "Alice Student", BookingStatus.CONFIRMED,
                1,
                List.of(
                        new BookingPlayerResponseDto(aliceStudent.getId(), "Alice Student", "23MIC1001", 1, "QR_ALICE", false, 1, 5),
                        new BookingPlayerResponseDto(charlieStudent.getId(), "Charlie Teammate", "23MIC2002", 2, "QR_CHARLIE", false, 1, 5)
                ),
                Instant.now()
        );

        when(bookingService.getBookingById(55L)).thenReturn(responseDto);

        // 1. Host (Alice) can view
        mockMvc.perform(get("/api/bookings/55")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(55));

        // 2. Teammate (Charlie) can view
        mockMvc.perform(get("/api/bookings/55")
                        .header("Authorization", "Bearer " + secondStudentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(55));

        // 3. Staff (Bob) can view
        mockMvc.perform(get("/api/bookings/55")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(55));
    }

    @Test
    @DisplayName("GET /api/bookings/{id} rejects non-participating student with HTTP 403 Forbidden")
    void shouldRejectNonParticipatingStudentFromInspectingBooking() throws Exception {
        // Booking between Alice and Bob; Charlie is NOT in this booking
        BookingResponseDto responseDto = new BookingResponseDto(
                77L, 10L, 1, LocalDate.of(2026, 10, 15),
                LocalDateTime.of(2026, 10, 15, 6, 0),
                LocalDateTime.of(2026, 10, 15, 6, 30),
                aliceStudent.getId(), "Alice Student", BookingStatus.CONFIRMED,
                1,
                List.of(
                        new BookingPlayerResponseDto(aliceStudent.getId(), "Alice Student", "23MIC1001", 1, "QR_ALICE", false, 1, 5),
                        new BookingPlayerResponseDto(bobStaff.getId(), "Bob Marshal", "23STAFF01", 2, "QR_BOB", false, 1, 5)
                ),
                Instant.now()
        );

        when(bookingService.getBookingById(77L)).thenReturn(responseDto);

        // Charlie (non-participant) attempts to view Alice and Bob's booking
        mockMvc.perform(get("/api/bookings/77")
                        .header("Authorization", "Bearer " + secondStudentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied: You are not authorized to view this booking."));
    }

    // ==========================================
    // 5. Booking Queue Authorization & Ownership
    // ==========================================

    @Test
    @DisplayName("POST /api/queue/join without authentication is rejected with HTTP 401 Unauthorized")
    void shouldRejectUnauthenticatedQueueJoin() throws Exception {
        mockMvc.perform(post("/api/queue/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\": 1, \"date\": \"2026-10-15\", \"sessionType\": \"MORNING\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/queue/join rejects student attempting to join queue for another student with HTTP 403 Forbidden")
    void shouldRejectQueueJoinForAnotherStudent() throws Exception {
        // Alice attempts to join queue using Charlie's ID
        String json = String.format("""
                {
                    "studentId": %d,
                    "date": "2026-10-15",
                    "sessionType": "MORNING"
                }
                """, charlieStudent.getId());

        mockMvc.perform(post("/api/queue/join")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied: You cannot enter another student into the booking queue."));
    }

    @Test
    @DisplayName("POST /api/queue/join allows authenticated student to join queue for themselves")
    void shouldAllowStudentToJoinQueueForThemselves() throws Exception {
        QueueEntryResponseDto mockEntry = new QueueEntryResponseDto(
                1L, 10L, aliceStudent.getId(), "Alice Student",
                LocalDate.of(2026, 10, 15), SessionType.MORNING,
                QueueEntryStatus.WAITING, 1L, Instant.now(), null, null, null,
                "Successfully joined queue at position 1."
        );
        when(bookingQueueService.joinQueue(any())).thenReturn(mockEntry);

        String json = String.format("""
                {
                    "studentId": %d,
                    "date": "2026-10-15",
                    "sessionType": "MORNING"
                }
                """, aliceStudent.getId());

        mockMvc.perform(post("/api/queue/join")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.positionInLine").value(1));
    }

    @Test
    @DisplayName("POST /api/queue/admit-next called by a STUDENT is rejected with HTTP 403 Forbidden")
    void shouldRejectStudentFromAdmitNext() throws Exception {
        mockMvc.perform(post("/api/queue/admit-next?date=2026-10-15&sessionType=MORNING")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("POST /api/queue/admit-next called by STAFF or ADMIN is authorized")
    void shouldAllowStaffOrAdminAdmitNext() throws Exception {
        AdmitNextResponseDto mockResponse = new AdmitNextResponseDto(
                1L, LocalDate.of(2026, 10, 15), SessionType.MORNING, true,
                501L, aliceStudent.getId(), "Alice Student",
                Instant.now(), Instant.now().plusSeconds(60), 60L, 0L,
                "Admitted next student."
        );
        when(bookingQueueService.admitNextStudent(any(), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/queue/admit-next?date=2026-10-15&sessionType=MORNING")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentAdmitted").value(true));
    }

    @Test
    @DisplayName("GET /api/queue/position allows student to inspect their own queue entry")
    void shouldAllowStudentToInspectOwnQueuePosition() throws Exception {
        QueueEntryResponseDto mockEntry = new QueueEntryResponseDto(
                1L, 10L, aliceStudent.getId(), "Alice Student",
                LocalDate.of(2026, 10, 15), SessionType.MORNING,
                QueueEntryStatus.WAITING, 1L, Instant.now(), null, null, null,
                "Position 1"
        );
        when(bookingQueueService.getStudentPosition(eq(aliceStudent.getId()), any(), any())).thenReturn(mockEntry);

        mockMvc.perform(get("/api/queue/position?date=2026-10-15&sessionType=MORNING")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionInLine").value(1));
    }

    @Test
    @DisplayName("GET /api/queue/position rejects student attempting to inspect another student's position with HTTP 403")
    void shouldRejectStudentInspectingAnotherStudentQueuePosition() throws Exception {
        mockMvc.perform(get("/api/queue/position?studentId=" + charlieStudent.getId() + "&date=2026-10-15&sessionType=MORNING")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied: You are not authorized to inspect another student's queue position."));
    }

    // ==========================================
    // 6. Admin-Only Operations (Slot Generation)
    // ==========================================

    @Test
    @DisplayName("POST /api/slots/generate without authentication is rejected with HTTP 401")
    void shouldRejectUnauthenticatedSlotGeneration() throws Exception {
        mockMvc.perform(post("/api/slots/generate?date=2026-10-15"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/slots/generate called by STUDENT is rejected with HTTP 403 Forbidden")
    void shouldRejectStudentFromGeneratingSlots() throws Exception {
        mockMvc.perform(post("/api/slots/generate?date=2026-10-15")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/slots/generate called by STAFF is authorized")
    void shouldAllowStaffToGenerateSlots() throws Exception {
        SlotGenerationResponseDto mockResponse = new SlotGenerationResponseDto(
                LocalDate.of(2026, 10, 15), 48, 48, 0, "Created 48 time slots for date 2026-10-15."
        );
        when(timeSlotService.generateSlotsForDate(any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/slots/generate?date=2026-10-15")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdSlots").value(48));
    }

    @Test
    @DisplayName("POST /api/slots/generate called by ADMIN is authorized")
    void shouldAllowAdminToGenerateSlots() throws Exception {
        SlotGenerationResponseDto mockResponse = new SlotGenerationResponseDto(
                LocalDate.of(2026, 10, 15), 48, 48, 0, "Created 48 time slots for date 2026-10-15."
        );
        when(timeSlotService.generateSlotsForDate(any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/slots/generate?date=2026-10-15")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdSlots").value(48));
    }

    // ==========================================
    // 7. Check-In & No-Show Staff Authorization (Preserved)
    // ==========================================

    @Test
    @DisplayName("POST /api/check-in without authentication is strictly rejected with HTTP 401 Unauthorized")
    void shouldRejectUnauthenticatedCheckIn() throws Exception {
        String json = """
                {
                    "qrToken": "QR_SOME_TOKEN"
                }
                """;

        mockMvc.perform(post("/api/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("POST /api/check-in called by a STUDENT role is rejected with HTTP 403 Forbidden")
    void shouldRejectStudentRoleFromCheckIn() throws Exception {
        String json = """
                {
                    "qrToken": "QR_SOME_TOKEN"
                }
                """;

        mockMvc.perform(post("/api/check-in")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("POST /api/check-in called by a STAFF role is authorized and derives staff identity")
    void shouldAllowStaffRoleCheckIn() throws Exception {
        CheckInResponseDto mockResponse = new CheckInResponseDto(
                true,
                "Check-in verified successfully.",
                false,
                10L,
                20L,
                aliceStudent.getId(),
                "Alice Student",
                "23MIC1001",
                1,
                1L,
                1,
                LocalDateTime.of(2026, 10, 15, 6, 0),
                LocalDateTime.of(2026, 10, 15, 6, 30),
                Instant.now(),
                "Bob Marshal (23STAFF01)"
        );

        when(checkInService.checkInPlayer(any(CheckInRequestDto.class), any())).thenReturn(mockResponse);

        String json = """
                {
                    "qrToken": "QR_VALID_TOKEN_123"
                }
                """;

        mockMvc.perform(post("/api/check-in")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.studentName").value("Alice Student"))
                .andExpect(jsonPath("$.checkedInBy").value("Bob Marshal (23STAFF01)"));
    }

    @Test
    @DisplayName("POST /api/check-in/process-no-shows without authentication is rejected with HTTP 401 Unauthorized")
    void shouldRejectUnauthenticatedNoShowProcessing() throws Exception {
        mockMvc.perform(post("/api/check-in/process-no-shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("POST /api/check-in/process-no-shows called by a STUDENT role is rejected with HTTP 403 Forbidden")
    void shouldRejectStudentRoleFromNoShowProcessing() throws Exception {
        mockMvc.perform(post("/api/check-in/process-no-shows")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("POST /api/check-in/process-no-shows called by an ADMIN role is authorized")
    void shouldAllowAdminRoleNoShowProcessing() throws Exception {
        NoShowProcessingResponseDto mockResponse = new NoShowProcessingResponseDto(
                0,
                List.of(),
                "No pending un-checked-in players found beyond the grace period deadline."
        );

        when(checkInService.processNoShows(any(), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/check-in/process-no-shows")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedCount").value(0));
    }

    @Test
    @DisplayName("POST /api/check-in/process-no-shows called by a STAFF role is authorized")
    void shouldAllowStaffRoleNoShowProcessing() throws Exception {
        NoShowProcessingResponseDto mockResponse = new NoShowProcessingResponseDto(
                0,
                List.of(),
                "No pending un-checked-in players found beyond the grace period deadline."
        );

        when(checkInService.processNoShows(any(), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/check-in/process-no-shows")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedCount").value(0));
    }

    @Test
    @DisplayName("POST /api/credits/reset-weekly without authentication is rejected with 401 Unauthorized")
    void shouldRejectAnonymousWeeklyCreditReset() throws Exception {
        mockMvc.perform(post("/api/credits/reset-weekly"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("POST /api/credits/reset-weekly by a STUDENT role is rejected with 403 Forbidden")
    void shouldRejectStudentRoleWeeklyCreditReset() throws Exception {
        mockMvc.perform(post("/api/credits/reset-weekly")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("POST /api/credits/reset-weekly by a STAFF role is authorized")
    void shouldAllowStaffRoleWeeklyCreditReset() throws Exception {
        when(creditResetService.resetWeeklyCredits(any()))
                .thenReturn(CreditResetResultDto.success("2026-W43", Instant.now(), 5, "Staff User"));

        mockMvc.perform(post("/api/credits/reset-weekly")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executed").value(true));
    }

    @Test
    @DisplayName("POST /api/credits/reset-weekly by an ADMIN role is authorized")
    void shouldAllowAdminRoleWeeklyCreditReset() throws Exception {
        when(creditResetService.resetWeeklyCredits(any()))
                .thenReturn(CreditResetResultDto.success("2026-W43", Instant.now(), 5, "Admin User"));

        mockMvc.perform(post("/api/credits/reset-weekly")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executed").value(true));
    }

    @Test
    @DisplayName("GET /api/credits/reset-status by a STUDENT role is rejected with 403 Forbidden")
    void shouldRejectStudentRoleCreditResetStatus() throws Exception {
        mockMvc.perform(get("/api/credits/reset-status")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("GET /api/credits/reset-status by a STAFF role is authorized")
    void shouldAllowStaffRoleCreditResetStatus() throws Exception {
        when(creditResetService.getCurrentPeriodKey()).thenReturn("2026-W43");
        when(creditResetService.isCurrentWeekResetCompleted()).thenReturn(true);
        when(creditResetService.getZoneId()).thenReturn(java.time.ZoneId.of("Asia/Kolkata"));

        mockMvc.perform(get("/api/credits/reset-status")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPeriodKey").value("2026-W43"));
    }

    @Test
    @DisplayName("GET /api/courts without authentication is permitted (public)")
    void shouldAllowAnonymousAccessToGetCourts() throws Exception {
        when(courtService.getAllCourts()).thenReturn(List.of(new CourtResponseDto(1L, 1, true)));

        mockMvc.perform(get("/api/courts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courtNumber").value(1));
    }

    @Test
    @DisplayName("GET /api/courts/{id} without authentication is permitted (public)")
    void shouldAllowAnonymousAccessToGetCourtById() throws Exception {
        when(courtService.getCourtById(1L)).thenReturn(new CourtResponseDto(1L, 1, true));

        mockMvc.perform(get("/api/courts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courtNumber").value(1));
    }

    @Test
    @DisplayName("PATCH /api/courts/{id}/status without authentication is rejected with 401 Unauthorized")
    void shouldRejectAnonymousCourtStatusUpdate() throws Exception {
        mockMvc.perform(patch("/api/courts/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("PATCH /api/courts/{id}/status by a STUDENT role is rejected with 403 Forbidden")
    void shouldRejectStudentRoleCourtStatusUpdate() throws Exception {
        mockMvc.perform(patch("/api/courts/1/status")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("PATCH /api/courts/{id}/status by a STAFF role is authorized")
    void shouldAllowStaffRoleCourtStatusUpdate() throws Exception {
        when(courtService.updateCourtStatus(eq(1L), any()))
                .thenReturn(new CourtResponseDto(1L, 1, false));

        mockMvc.perform(patch("/api/courts/1/status")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("PATCH /api/courts/{id}/status by an ADMIN role is authorized")
    void shouldAllowAdminRoleCourtStatusUpdate() throws Exception {
        when(courtService.updateCourtStatus(eq(1L), any()))
                .thenReturn(new CourtResponseDto(1L, 1, true));

        mockMvc.perform(patch("/api/courts/1/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("PUT /api/courts/{id}/status by a STUDENT role is rejected with 403 Forbidden")
    void shouldRejectStudentRoleCourtStatusPut() throws Exception {
        mockMvc.perform(put("/api/courts/1/status")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("PUT /api/courts/{id}/status by a STAFF role is authorized")
    void shouldAllowStaffRoleCourtStatusPut() throws Exception {
        when(courtService.updateCourtStatus(eq(1L), any()))
                .thenReturn(new CourtResponseDto(1L, 1, false));

        mockMvc.perform(put("/api/courts/1/status")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("PUT /api/sessions/{id} without authorization header is rejected with 401 Unauthorized")
    void shouldRejectUnauthenticatedSessionConfigPut() throws Exception {
        mockMvc.perform(put("/api/sessions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotDurationMinutes\":45}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("PUT /api/sessions/{id} by a STUDENT role is rejected with 403 Forbidden")
    void shouldRejectStudentRoleSessionConfigPut() throws Exception {
        mockMvc.perform(put("/api/sessions/1")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotDurationMinutes\":45}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("PUT /api/sessions/{id} by a STAFF role is authorized")
    void shouldAllowStaffRoleSessionConfigPut() throws Exception {
        when(sessionConfigService.updateSessionConfig(eq(1L), any()))
                .thenReturn(new SessionConfigResponseDto(
                        1L,
                        SessionType.MORNING,
                        LocalTime.of(6, 0),
                        LocalTime.of(8, 30),
                        45,
                        LocalTime.of(21, 0),
                        LocalTime.of(21, 5),
                        1,
                        true
                ));

        mockMvc.perform(put("/api/sessions/1")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotDurationMinutes\":45}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotDurationMinutes").value(45));
    }

    @Test
    @DisplayName("PUT /api/sessions/{id} by an ADMIN role is authorized")
    void shouldAllowAdminRoleSessionConfigPut() throws Exception {
        when(sessionConfigService.updateSessionConfig(eq(1L), any()))
                .thenReturn(new SessionConfigResponseDto(
                        1L,
                        SessionType.MORNING,
                        LocalTime.of(6, 0),
                        LocalTime.of(8, 30),
                        30,
                        LocalTime.of(21, 0),
                        LocalTime.of(21, 5),
                        1,
                        true
                ));

        mockMvc.perform(put("/api/sessions/1")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotDurationMinutes\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotDurationMinutes").value(30));
    }

    @Test
    @DisplayName("PATCH /api/sessions/{id} by a STUDENT role is rejected with 403 Forbidden")
    void shouldRejectStudentRoleSessionConfigPatch() throws Exception {
        mockMvc.perform(patch("/api/sessions/1")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("PATCH /api/sessions/{id} by a STAFF role is authorized")
    void shouldAllowStaffRoleSessionConfigPatch() throws Exception {
        when(sessionConfigService.updateSessionConfig(eq(1L), any()))
                .thenReturn(new SessionConfigResponseDto(
                        1L,
                        SessionType.MORNING,
                        LocalTime.of(6, 0),
                        LocalTime.of(8, 30),
                        30,
                        LocalTime.of(21, 0),
                        LocalTime.of(21, 5),
                        1,
                        false
                ));

        mockMvc.perform(patch("/api/sessions/1")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("PATCH /api/sessions/{id} without authorization header is rejected with 401 Unauthorized")
    void shouldRejectAnonymousSessionConfigPatch() throws Exception {
        mockMvc.perform(patch("/api/sessions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("PATCH /api/sessions/{id} by an ADMIN role is authorized")
    void shouldAllowAdminRoleSessionConfigPatch() throws Exception {
        when(sessionConfigService.updateSessionConfig(eq(1L), any()))
                .thenReturn(new SessionConfigResponseDto(
                        1L,
                        SessionType.MORNING,
                        LocalTime.of(6, 0),
                        LocalTime.of(8, 30),
                        30,
                        LocalTime.of(21, 0),
                        LocalTime.of(21, 5),
                        1,
                        true
                ));

        mockMvc.perform(patch("/api/sessions/1")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    // ==========================================
    // 8. Complete Cross-Role Matrix Verifications
    // ==========================================

    @Test
    @DisplayName("GET /api/courts and /api/courts/{id} accessible by STUDENT, STAFF, and ADMIN roles")
    void shouldAllowAllRolesToViewCourtsAndCourtDetails() throws Exception {
        when(courtService.getAllCourts()).thenReturn(List.of(new CourtResponseDto(1L, 1, true)));
        when(courtService.getCourtById(1L)).thenReturn(new CourtResponseDto(1L, 1, true));

        // STUDENT
        mockMvc.perform(get("/api/courts").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/courts/1").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        // STAFF
        mockMvc.perform(get("/api/courts").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/courts/1").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk());

        // ADMIN
        mockMvc.perform(get("/api/courts").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/courts/1").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/courts/{id}/status without authentication is rejected with 401 Unauthorized")
    void shouldRejectAnonymousCourtStatusPut() throws Exception {
        mockMvc.perform(put("/api/courts/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("PUT /api/courts/{id}/status by an ADMIN role is authorized")
    void shouldAllowAdminRoleCourtStatusPut() throws Exception {
        when(courtService.updateCourtStatus(eq(1L), any()))
                .thenReturn(new CourtResponseDto(1L, 1, true));

        mockMvc.perform(put("/api/courts/1/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("GET /api/slots accessible by STUDENT, STAFF, and ADMIN roles")
    void shouldAllowAllRolesToViewTimeSlots() throws Exception {
        when(timeSlotService.getSlotsByDate(any())).thenReturn(List.of());

        // STUDENT
        mockMvc.perform(get("/api/slots?date=2026-10-15").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        // STAFF
        mockMvc.perform(get("/api/slots?date=2026-10-15").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk());

        // ADMIN
        mockMvc.perform(get("/api/slots?date=2026-10-15").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/sessions and /api/sessions/{id} accessible by Guest, STUDENT, STAFF, and ADMIN roles")
    void shouldAllowAllRolesToViewSessionRules() throws Exception {
        SessionConfigResponseDto sessionDto = new SessionConfigResponseDto(
                1L, SessionType.MORNING, LocalTime.of(6, 0), LocalTime.of(8, 30), 30,
                LocalTime.of(21, 0), LocalTime.of(21, 5), 1, true
        );
        when(sessionConfigService.getAllSessions()).thenReturn(List.of(sessionDto));
        when(sessionConfigService.getSessionById(1L)).thenReturn(sessionDto);

        // Guest
        mockMvc.perform(get("/api/sessions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionType").value("MORNING"));

        // STUDENT
        mockMvc.perform(get("/api/sessions").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/sessions/1").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        // STAFF
        mockMvc.perform(get("/api/sessions").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/sessions/1").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk());

        // ADMIN
        mockMvc.perform(get("/api/sessions").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/sessions/1").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/check-in called by an ADMIN role is authorized")
    void shouldAllowAdminRoleCheckIn() throws Exception {
        CheckInResponseDto mockResponse = new CheckInResponseDto(
                true,
                "Check-in verified successfully.",
                false,
                10L,
                20L,
                aliceStudent.getId(),
                "Alice Student",
                "23MIC1001",
                1,
                1L,
                1,
                LocalDateTime.of(2026, 10, 15, 6, 0),
                LocalDateTime.of(2026, 10, 15, 6, 30),
                Instant.now(),
                "Admin User (23ADMIN01)"
        );

        when(checkInService.checkInPlayer(any(CheckInRequestDto.class), any())).thenReturn(mockResponse);

        String json = """
                {
                    "qrToken": "QR_VALID_TOKEN_ADMIN"
                }
                """;

        mockMvc.perform(post("/api/check-in")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.checkedInBy").value("Admin User (23ADMIN01)"));
    }

    @Test
    @DisplayName("GET /api/credits/reset-status without authentication is rejected with 401 Unauthorized")
    void shouldRejectAnonymousCreditResetStatus() throws Exception {
        mockMvc.perform(get("/api/credits/reset-status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("GET /api/credits/reset-status by an ADMIN role is authorized")
    void shouldAllowAdminRoleCreditResetStatus() throws Exception {
        when(creditResetService.getCurrentPeriodKey()).thenReturn("2026-W43");
        when(creditResetService.isCurrentWeekResetCompleted()).thenReturn(true);
        when(creditResetService.getZoneId()).thenReturn(java.time.ZoneId.of("Asia/Kolkata"));

        mockMvc.perform(get("/api/credits/reset-status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPeriodKey").value("2026-W43"));
    }
}
