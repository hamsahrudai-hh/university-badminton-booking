package edu.university.badminton.controller;

import edu.university.badminton.dto.StudentResponseDto;
import edu.university.badminton.dto.TeammateResponseDto;
import edu.university.badminton.service.StudentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * REST controller for student profile and credit queries.
 * <p>
 * Full student profiles (including private admission number, email, and credit balance)
 * are protected and accessible only by the authenticated owner or staff/admin.
 * Public teammate listings expose only minimal fields (ID, display name, eligibility)
 * to safeguard student privacy.
 */
@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    /**
     * Retrieves a student's profile, role, active status, and current weekly credit balance.
     * Enforces ownership: students can only access their own profile; STAFF/ADMIN can inspect any profile.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getStudentById(
            @PathVariable("id") Long id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal edu.university.badminton.config.UserPrincipal principal) {
        if (principal != null) {
            boolean isOwner = principal.id() != null && principal.id().equals(id);
            boolean isStaffOrAdmin = principal.role() == edu.university.badminton.model.Role.STAFF
                    || principal.role() == edu.university.badminton.model.Role.ADMIN;

            if (!isOwner && !isStaffOrAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied: You are not authorized to view another student's profile."));
            }
        }

        try {
            return ResponseEntity.ok(studentService.getStudentById(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Lists all registered active students for teammate group selection in a privacy-safe format.
     * Only returns ID, display name, and eligibility status.
     * Sensitive fields (email, admission number, weekly credits, reliability rating) are omitted.
     */
    @GetMapping
    public ResponseEntity<List<TeammateResponseDto>> getAllStudents() {
        return ResponseEntity.ok(studentService.getAllTeammateCandidates());
    }
}
