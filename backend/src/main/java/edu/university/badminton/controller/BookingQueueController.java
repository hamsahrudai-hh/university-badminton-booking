package edu.university.badminton.controller;

import edu.university.badminton.dto.AdmitNextResponseDto;
import edu.university.badminton.dto.JoinQueueRequestDto;
import edu.university.badminton.dto.QueueEntryResponseDto;
import edu.university.badminton.dto.QueueStatusResponseDto;
import edu.university.badminton.model.SessionType;
import edu.university.badminton.service.BookingQueueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * REST controller for the student booking queue and 60-second booking opportunity.
 * <p>
 * <strong>Important Security Note:</strong> Client-supplied {@code studentId} is a temporary development
 * mechanism until Phase 3 authentication (Spring Security / JWT) is configured.
 */
@RestController
@RequestMapping("/api/queue")
public class BookingQueueController {

    private final BookingQueueService bookingQueueService;

    public BookingQueueController(BookingQueueService bookingQueueService) {
        this.bookingQueueService = bookingQueueService;
    }

    /**
     * Enters a student into the booking queue for a specific date and session.
     * Enforces ownership: a student can only join the queue for themselves.
     */
    @PostMapping("/join")
    public ResponseEntity<?> joinQueue(
            @RequestBody(required = false) JoinQueueRequestDto request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal edu.university.badminton.config.UserPrincipal principal) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body is required."));
        }
        if (principal != null) {
            if (request.studentId() != null && !request.studentId().equals(principal.id())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied: You cannot enter another student into the booking queue."));
            }
        }
        Long effectiveStudentId = principal != null ? principal.id() : request.studentId();
        if (effectiveStudentId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "The 'studentId' field is required."));
        }
        if (request.date() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "The 'date' field is required (format: YYYY-MM-DD)."));
        }
        if (request.sessionType() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "The 'sessionType' field is required (MORNING or EVENING)."));
        }

        JoinQueueRequestDto securedRequest = new JoinQueueRequestDto(
                effectiveStudentId,
                request.date(),
                request.sessionType()
        );

        try {
            QueueEntryResponseDto response = bookingQueueService.joinQueue(securedRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("inactive")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
            }
            if (e.getMessage().contains("already in this booking queue")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
            }
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Atomically admits the next waiting student in FIFO order to hold the active 60-second booking opportunity.
     * Restricted to STAFF or ADMIN roles.
     */
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @PostMapping("/admit-next")
    public ResponseEntity<?> admitNext(
            @RequestParam(value = "date", required = false) String dateStr,
            @RequestParam(value = "sessionType", required = false) String sessionTypeStr) {

        LocalDate date = parseDate(dateStr);
        if (date == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "A valid 'date' parameter is required (YYYY-MM-DD)."));
        }

        SessionType sessionType = parseSessionType(sessionTypeStr);
        if (sessionType == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "A valid 'sessionType' parameter is required (MORNING or EVENING)."));
        }

        try {
            AdmitNextResponseDto response = bookingQueueService.admitNextStudent(date, sessionType);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Inspects the current queue status, counts, and active opportunity window.
     */
    @GetMapping("/status")
    public ResponseEntity<?> getQueueStatus(
            @RequestParam(value = "date", required = false) String dateStr,
            @RequestParam(value = "sessionType", required = false) String sessionTypeStr) {

        LocalDate date = parseDate(dateStr);
        if (date == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "A valid 'date' parameter is required (YYYY-MM-DD)."));
        }

        SessionType sessionType = parseSessionType(sessionTypeStr);
        if (sessionType == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "A valid 'sessionType' parameter is required (MORNING or EVENING)."));
        }

        try {
            QueueStatusResponseDto response = bookingQueueService.getQueueStatus(date, sessionType);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Checks an individual student's queue entry, FIFO line position, or active opportunity.
     * Enforces ownership: students can only inspect their own queue entry; STAFF/ADMIN can inspect any entry.
     */
    @GetMapping("/position")
    public ResponseEntity<?> getStudentPosition(
            @RequestParam(value = "studentId", required = false) Long studentId,
            @RequestParam(value = "date", required = false) String dateStr,
            @RequestParam(value = "sessionType", required = false) String sessionTypeStr,
            @org.springframework.security.core.annotation.AuthenticationPrincipal edu.university.badminton.config.UserPrincipal principal) {

        Long targetStudentId = studentId;
        if (principal != null) {
            targetStudentId = studentId != null ? studentId : principal.id();
            boolean isOwner = targetStudentId.equals(principal.id());
            boolean isStaffOrAdmin = principal.role() == edu.university.badminton.model.Role.STAFF
                    || principal.role() == edu.university.badminton.model.Role.ADMIN;

            if (!isOwner && !isStaffOrAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied: You are not authorized to inspect another student's queue position."));
            }
        } else if (targetStudentId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "The 'studentId' query parameter is required."));
        }

        LocalDate date = parseDate(dateStr);
        if (date == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "A valid 'date' parameter is required (YYYY-MM-DD)."));
        }

        SessionType sessionType = parseSessionType(sessionTypeStr);
        if (sessionType == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "A valid 'sessionType' parameter is required (MORNING or EVENING)."));
        }

        try {
            QueueEntryResponseDto response = bookingQueueService.getStudentPosition(targetStudentId, date, sessionType);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private SessionType parseSessionType(String sessionTypeStr) {
        if (sessionTypeStr == null || sessionTypeStr.isBlank()) {
            return null;
        }
        try {
            return SessionType.valueOf(sessionTypeStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
