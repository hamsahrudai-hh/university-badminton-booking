package edu.university.badminton.controller;

import edu.university.badminton.config.UserPrincipal;
import edu.university.badminton.dto.CreditResetResultDto;
import edu.university.badminton.model.CreditResetExecution;
import edu.university.badminton.service.CreditResetService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for facility staff and administrators to inspect and manually trigger weekly credit resets.
 * Strictly requires authenticated STAFF or ADMIN role.
 * <p>
 * Idempotency is strictly authoritative: no override exists to force repeat resets in the same week.
 */
@RestController
@RequestMapping("/api/credits")
@PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
public class CreditResetController {

    private final CreditResetService creditResetService;

    public CreditResetController(CreditResetService creditResetService) {
        this.creditResetService = creditResetService;
    }

    /**
     * Manually triggers the weekly credit reset.
     * Strictly respects weekly period deduplication. If already executed for the week,
     * the operation safely skips and returns the authoritative previous execution audit.
     */
    @PostMapping("/reset-weekly")
    public ResponseEntity<CreditResetResultDto> triggerWeeklyCreditReset(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        String staffId = userPrincipal != null
                ? userPrincipal.fullName() + " (" + userPrincipal.admissionNumber() + ")"
                : "MANUAL_TRIGGER";

        CreditResetResultDto result = creditResetService.resetWeeklyCredits(staffId);
        return ResponseEntity.ok(result);
    }

    /**
     * Inspects the current week's credit reset status and recent execution history.
     */
    @GetMapping("/reset-status")
    public ResponseEntity<Map<String, Object>> getResetStatus() {
        String currentPeriodKey = creditResetService.getCurrentPeriodKey();
        boolean currentWeekExecuted = creditResetService.isCurrentWeekResetCompleted();
        Optional<CreditResetExecution> currentExecution = creditResetService.getExecutionForCurrentWeek();
        Optional<CreditResetExecution> latestExecution = creditResetService.getLatestExecution();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("currentPeriodKey", currentPeriodKey);
        response.put("currentWeekResetExecuted", currentWeekExecuted);
        response.put("universityTimezone", creditResetService.getZoneId().getId());
        response.put("currentWeekExecution", currentExecution.orElse(null));
        response.put("latestExecution", latestExecution.orElse(null));

        return ResponseEntity.ok(response);
    }
}
