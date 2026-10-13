package edu.university.badminton.controller;

import edu.university.badminton.dto.SlotGenerationResponseDto;
import edu.university.badminton.dto.TimeSlotResponseDto;
import edu.university.badminton.service.TimeSlotService;
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
import java.util.List;
import java.util.Map;

/**
 * REST controller for time slot queries and generation.
 */
@RestController
@RequestMapping("/api/slots")
public class TimeSlotController {

    private final TimeSlotService timeSlotService;

    public TimeSlotController(TimeSlotService timeSlotService) {
        this.timeSlotService = timeSlotService;
    }

    @GetMapping
    public ResponseEntity<?> getSlotsByDate(@RequestParam(value = "date", required = false) String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "The 'date' query parameter is required (format: YYYY-MM-DD).")
            );
        }

        LocalDate date;
        try {
            date = LocalDate.parse(dateStr.trim());
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Invalid date format. Expected ISO-8601 date (YYYY-MM-DD).")
            );
        }

        List<TimeSlotResponseDto> slots = timeSlotService.getSlotsByDate(date);
        return ResponseEntity.ok(slots);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @PostMapping("/generate")
    public ResponseEntity<?> generateSlots(
            @RequestParam(value = "date", required = false) String dateParam,
            @RequestBody(required = false) Map<String, String> body) {
        String dateStr = dateParam;
        if ((dateStr == null || dateStr.isBlank()) && body != null) {
            dateStr = body.get("date");
        }

        if (dateStr == null || dateStr.isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "The 'date' parameter is required in ISO format (YYYY-MM-DD).")
            );
        }

        LocalDate date;
        try {
            date = LocalDate.parse(dateStr.trim());
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Invalid date format. Expected ISO-8601 date (YYYY-MM-DD).")
            );
        }

        try {
            SlotGenerationResponseDto result = timeSlotService.generateSlotsForDate(date);
            HttpStatus status = result.createdSlots() > 0 ? HttpStatus.CREATED : HttpStatus.OK;
            return ResponseEntity.status(status).body(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    Map.of("error", e.getMessage())
            );
        }
    }
}
