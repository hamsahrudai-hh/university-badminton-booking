package edu.university.badminton.controller;

import edu.university.badminton.dto.SessionConfigResponseDto;
import edu.university.badminton.dto.UpdateSessionConfigRequestDto;
import edu.university.badminton.service.SessionConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * REST controller for session configuration queries and administrative rule management.
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionConfigController {

    private final SessionConfigService sessionConfigService;

    public SessionConfigController(SessionConfigService sessionConfigService) {
        this.sessionConfigService = sessionConfigService;
    }

    /**
     * Public endpoint to view all database-backed recurring session configurations.
     */
    @GetMapping
    public ResponseEntity<List<SessionConfigResponseDto>> getSessions() {
        return ResponseEntity.ok(sessionConfigService.getAllSessions());
    }

    /**
     * Public endpoint to view a specific session configuration by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getSessionById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(sessionConfigService.getSessionById(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Administrative endpoint to update session rules (PUT).
     * Strictly restricted to authorized STAFF and ADMIN users.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<?> updateSessionConfigPut(
            @PathVariable Long id,
            @RequestBody UpdateSessionConfigRequestDto request
    ) {
        return handleUpdateSessionConfig(id, request);
    }

    /**
     * Administrative endpoint to update session rules (PATCH).
     * Strictly restricted to authorized STAFF and ADMIN users.
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<?> updateSessionConfigPatch(
            @PathVariable Long id,
            @RequestBody UpdateSessionConfigRequestDto request
    ) {
        return handleUpdateSessionConfig(id, request);
    }

    private ResponseEntity<?> handleUpdateSessionConfig(Long id, UpdateSessionConfigRequestDto request) {
        try {
            SessionConfigResponseDto response = sessionConfigService.updateSessionConfig(id, request);
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
