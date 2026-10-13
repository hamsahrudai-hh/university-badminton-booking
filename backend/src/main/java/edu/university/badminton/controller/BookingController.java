package edu.university.badminton.controller;

import edu.university.badminton.dto.BookingResponseDto;
import edu.university.badminton.dto.CreateBookingRequestDto;
import edu.university.badminton.service.BookingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * REST controller for executing and inspecting court reservations.
 * <p>
 * <strong>Important Security Note:</strong> Client-supplied student IDs are a temporary development
 * mechanism until Phase 3 authentication (Spring Security / JWT) is configured. It must not be presented
 * as production-secure identity.
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * Confirms a group booking atomically: deducts 1 weekly credit per group member,
     * reserves the court slot, creates individual player passes with QR tokens,
     * and completes the admitted queue entry.
     * Enforces host identity: the booking host must be the authenticated user.
     */
    @PostMapping
    public ResponseEntity<?> confirmBooking(
            @RequestBody(required = false) CreateBookingRequestDto request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal edu.university.badminton.config.UserPrincipal principal) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body is required."));
        }

        if (principal != null) {
            // Ownership enforcement: the authenticated student must be the booking host
            if (request.bookingStudentId() != null && !request.bookingStudentId().equals(principal.id())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied: You cannot create a booking on behalf of another student."));
            }
        }

        Long effectiveHostId = principal != null ? principal.id() : request.bookingStudentId();

        // Authoritatively enforce the authenticated student ID as the booking host
        CreateBookingRequestDto securedRequest = new CreateBookingRequestDto(
                effectiveHostId,
                request.timeSlotId(),
                request.players(),
                request.idempotencyKey()
        );

        // Group booking validation: ensure authenticated student is included in the group
        if (principal != null && securedRequest.players() != null && !securedRequest.players().isEmpty()) {
            boolean hostIncluded = securedRequest.players().stream()
                    .anyMatch(p -> p.studentId() != null && p.studentId().equals(principal.id()));
            if (!hostIncluded) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "The authenticated booking host must be included as one of the group players."));
            }
        }

        try {
            BookingResponseDto response = bookingService.confirmBooking(securedRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Retrieves details of a confirmed booking by its ID.
     * Enforces participant privacy: only participating group members, STAFF, or ADMIN can view.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getBookingById(
            @PathVariable("id") Long id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal edu.university.badminton.config.UserPrincipal principal) {
        try {
            BookingResponseDto booking = bookingService.getBookingById(id);

            if (principal != null) {
                boolean isStaffOrAdmin = principal.role() == edu.university.badminton.model.Role.STAFF
                        || principal.role() == edu.university.badminton.model.Role.ADMIN;
                boolean isHost = booking.bookingStudentId() != null && booking.bookingStudentId().equals(principal.id());
                boolean isPlayer = booking.players() != null && booking.players().stream()
                        .anyMatch(p -> p.studentId() != null && p.studentId().equals(principal.id()));

                if (!isStaffOrAdmin && !isHost && !isPlayer) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "Access denied: You are not authorized to view this booking."));
                }
            }

            return ResponseEntity.ok(booking);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}
