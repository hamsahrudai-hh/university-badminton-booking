package edu.university.badminton.controller;

import edu.university.badminton.dto.CourtResponseDto;
import edu.university.badminton.dto.UpdateCourtStatusRequestDto;
import edu.university.badminton.service.CourtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * REST controller for court queries and administrative status management.
 */
@RestController
@RequestMapping("/api/courts")
public class CourtController {

    private final CourtService courtService;

    public CourtController(CourtService courtService) {
        this.courtService = courtService;
    }

    /**
     * Public endpoint to view all courts or active courts.
     */
    @GetMapping
    public ResponseEntity<List<CourtResponseDto>> getCourts(
            @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly
    ) {
        if (activeOnly) {
            return ResponseEntity.ok(courtService.getActiveCourts());
        }
        return ResponseEntity.ok(courtService.getAllCourts());
    }

    /**
     * Public endpoint to view a specific court by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getCourtById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(courtService.getCourtById(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Administrative endpoint to activate or deactivate a court.
     * Strictly requires STAFF or ADMIN role.
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<?> updateCourtStatusPatch(
            @PathVariable Long id,
            @RequestBody(required = false) UpdateCourtStatusRequestDto request
    ) {
        return handleUpdateCourtStatus(id, request);
    }

    /**
     * Supports PUT method as well for client flexibility.
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<?> updateCourtStatusPut(
            @PathVariable Long id,
            @RequestBody(required = false) UpdateCourtStatusRequestDto request
    ) {
        return handleUpdateCourtStatus(id, request);
    }

    private ResponseEntity<?> handleUpdateCourtStatus(Long id, UpdateCourtStatusRequestDto request) {
        try {
            CourtResponseDto response = courtService.updateCourtStatus(id, request);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
