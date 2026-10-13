package edu.university.badminton.service;

import edu.university.badminton.dto.StudentResponseDto;
import edu.university.badminton.dto.TeammateResponseDto;
import edu.university.badminton.model.Student;
import edu.university.badminton.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Service for querying student profiles and registration rosters.
 */
@Service
@Transactional(readOnly = true)
public class StudentService {

    private final StudentRepository studentRepository;

    public StudentService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    public StudentResponseDto getStudentById(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Student not found with ID: " + id));
        return StudentResponseDto.fromEntity(student);
    }

    /**
     * Returns a privacy-safe roster of eligible teammates for group booking.
     * Only reveals student ID, display name, and eligibility status.
     */
    public List<TeammateResponseDto> getAllTeammateCandidates() {
        return studentRepository.findAll()
                .stream()
                .filter(s -> Boolean.TRUE.equals(s.getActive()) && Boolean.TRUE.equals(s.getEmailVerified()))
                .map(TeammateResponseDto::fromEntity)
                .toList();
    }

    public List<StudentResponseDto> getAllActiveStudents() {
        return studentRepository.findAll()
                .stream()
                .filter(s -> Boolean.TRUE.equals(s.getActive()) && Boolean.TRUE.equals(s.getEmailVerified()))
                .map(StudentResponseDto::fromEntity)
                .toList();
    }
}
