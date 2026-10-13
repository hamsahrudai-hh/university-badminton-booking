package edu.university.badminton.controller;

import edu.university.badminton.dto.StudentResponseDto;
import edu.university.badminton.model.Role;
import edu.university.badminton.service.StudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.NoSuchElementException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StudentControllerTest {

    @Mock
    private StudentService studentService;

    @InjectMocks
    private StudentController studentController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(studentController).build();
    }

    @Test
    @DisplayName("GET /api/students/{id} returns HTTP 200 and student details including weekly credits")
    void shouldReturnStudentById() throws Exception {
        StudentResponseDto dto = new StudentResponseDto(
                1L, "Alice Smith", "alice@test.edu", "2024BCS001", Role.STUDENT, 5, 100.0, true
        );
        when(studentService.getStudentById(1L)).thenReturn(dto);

        mockMvc.perform(get("/api/students/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.fullName").value("Alice Smith"))
                .andExpect(jsonPath("$.weeklyCredits").value(5))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("GET /api/students/{id} when not found returns HTTP 404")
    void shouldReturn404WhenStudentNotFound() throws Exception {
        when(studentService.getStudentById(999L))
                .thenThrow(new NoSuchElementException("Student not found with ID: 999"));

        mockMvc.perform(get("/api/students/999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("Student not found")));
    }

    @Test
    @DisplayName("GET /api/students returns privacy-safe list of teammates omitting private fields")
    void shouldReturnAllStudentsPrivacySafe() throws Exception {
        List<edu.university.badminton.dto.TeammateResponseDto> teammates = List.of(
                new edu.university.badminton.dto.TeammateResponseDto(1L, "Alice", true),
                new edu.university.badminton.dto.TeammateResponseDto(2L, "Bob", true)
        );
        when(studentService.getAllTeammateCandidates()).thenReturn(teammates);

        mockMvc.perform(get("/api/students")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].fullName").value("Alice"))
                .andExpect(jsonPath("$[0].eligible").value(true))
                .andExpect(jsonPath("$[0].universityEmail").doesNotExist())
                .andExpect(jsonPath("$[0].admissionNumber").doesNotExist())
                .andExpect(jsonPath("$[0].weeklyCredits").doesNotExist())
                .andExpect(jsonPath("$[0].reliabilityRating").doesNotExist())
                .andExpect(jsonPath("$[0].role").doesNotExist())
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].fullName").value("Bob"))
                .andExpect(jsonPath("$[1].eligible").value(true))
                .andExpect(jsonPath("$[1].universityEmail").doesNotExist())
                .andExpect(jsonPath("$[1].admissionNumber").doesNotExist())
                .andExpect(jsonPath("$[1].weeklyCredits").doesNotExist());
    }
}
