package edu.university.badminton.controller;

import edu.university.badminton.config.UserPrincipal;
import edu.university.badminton.dto.CheckInRequestDto;
import edu.university.badminton.dto.CheckInResponseDto;
import edu.university.badminton.dto.NoShowProcessingResponseDto;
import edu.university.badminton.dto.ProcessNoShowsRequestDto;
import edu.university.badminton.service.CheckInService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * REST controller for facility staff court attendance check-in and no-show processing.
 * Strictly requires authenticated STAFF or ADMIN role.
 */
@RestController
@RequestMapping("/api/check-in")
@PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
public class CheckInController {

    private final CheckInService checkInService;

    public CheckInController(CheckInService checkInService) {
        this.checkInService = checkInService;
    }

    /**
     * Verifies and records a player's check-in via their scanned QR token.
     * Enforces the 10-minute grace period and handles duplicate scans idempotently.
     * Authoritative staff identity is extracted directly from the authenticated principal.
     */
    @PostMapping
    public ResponseEntity<?> checkInPlayer(
            @RequestBody(required = false) CheckInRequestDto request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body is required."));
        }
        try {
            String authenticatedStaffId = userPrincipal != null
                    ? userPrincipal.fullName() + " (" + userPrincipal.admissionNumber() + ")"
                    : null;
            CheckInResponseDto response = checkInService.checkInPlayer(request, authenticatedStaffId);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Executes no-show forfeiture processing for slots whose 10-minute grace period has passed.
     * Sets remaining weekly credits to 0 and adjusts reliability rating.
     * Safe to invoke repeatedly without double penalties.
     */
    @PostMapping("/process-no-shows")
    public ResponseEntity<?> processNoShows(
            @RequestBody(required = false) ProcessNoShowsRequestDto request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        Long slotId = request != null ? request.timeSlotId() : null;
        try {
            String authenticatedStaffId = userPrincipal != null
                    ? userPrincipal.fullName() + " (" + userPrincipal.admissionNumber() + ")"
                    : null;
            NoShowProcessingResponseDto response = checkInService.processNoShows(slotId, authenticatedStaffId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process no-shows: " + e.getMessage()));
        }
    }
}
