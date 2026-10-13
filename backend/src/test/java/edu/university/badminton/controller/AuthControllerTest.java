package edu.university.badminton.controller;

import edu.university.badminton.dto.RegisterStudentRequestDto;
import edu.university.badminton.dto.RegistrationResponseDto;
import edu.university.badminton.dto.VerifyEmailRequestDto;
import edu.university.badminton.dto.VerifyEmailResponseDto;
import edu.university.badminton.service.StudentRegistrationService;
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

import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import edu.university.badminton.dto.LoginRequestDto;
import edu.university.badminton.dto.LoginResponseDto;
import edu.university.badminton.model.Role;
import edu.university.badminton.service.AuthenticationService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private StudentRegistrationService registrationService;

    @Mock
    private AuthenticationService authenticationService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
    }

    @Test
    @DisplayName("POST /api/auth/register with valid input returns HTTP 201 Created")
    void shouldRegisterStudentSuccessfully() throws Exception {
        RegistrationResponseDto responseDto = new RegistrationResponseDto(
                1L,
                "Alice Bob",
                "alice.bob@vitapstudent.ac.in",
                "24BCS001",
                false,
                "Registration successful. Please verify your email."
        );

        when(registrationService.register(any(RegisterStudentRequestDto.class))).thenReturn(responseDto);

        String jsonBody = """
                {
                    "fullName": "Alice Bob",
                    "universityEmail": "alice.bob@vitapstudent.ac.in",
                    "admissionNumber": "24BCS001",
                    "password": "securePass123"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentId").value(1))
                .andExpect(jsonPath("$.fullName").value("Alice Bob"))
                .andExpect(jsonPath("$.universityEmail").value("alice.bob@vitapstudent.ac.in"))
                .andExpect(jsonPath("$.admissionNumber").value("24BCS001"))
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    @DisplayName("POST /api/auth/register without body returns HTTP 400 Bad Request")
    void shouldReturnBadRequestWhenRequestBodyMissing() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Registration request body is required."));
    }

    @Test
    @DisplayName("POST /api/auth/register with invalid domain returns HTTP 400 Bad Request")
    void shouldReturnBadRequestWhenDomainInvalid() throws Exception {
        when(registrationService.register(any())).thenThrow(new IllegalArgumentException("Email must end with @vitapstudent.ac.in"));

        String jsonBody = """
                {
                    "fullName": "Alice Bob",
                    "universityEmail": "alice@gmail.com",
                    "admissionNumber": "24BCS001",
                    "password": "securePass123"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Email must end with @vitapstudent.ac.in"));
    }

    @Test
    @DisplayName("POST /api/auth/register with duplicate email returns HTTP 409 Conflict")
    void shouldReturnConflictWhenEmailAlreadyExists() throws Exception {
        when(registrationService.register(any())).thenThrow(new IllegalStateException("A student with this university email already exists."));

        String jsonBody = """
                {
                    "fullName": "Alice Bob",
                    "universityEmail": "alice.bob@vitapstudent.ac.in",
                    "admissionNumber": "24BCS001",
                    "password": "securePass123"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("A student with this university email already exists."));
    }

    @Test
    @DisplayName("POST /api/auth/verify-email with valid token returns HTTP 200 OK")
    void shouldVerifyEmailViaPostSuccessfully() throws Exception {
        VerifyEmailResponseDto responseDto = new VerifyEmailResponseDto(
                1L,
                "Alice Bob",
                "alice.bob@vitapstudent.ac.in",
                true,
                6,
                "Email successfully verified. Account activated with 6 weekly credits."
        );

        when(registrationService.verifyEmail("valid-token-123")).thenReturn(responseDto);

        String jsonBody = """
                {
                    "token": "valid-token-123"
                }
                """;

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(1))
                .andExpect(jsonPath("$.fullName").value("Alice Bob"))
                .andExpect(jsonPath("$.universityEmail").value("alice.bob@vitapstudent.ac.in"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.weeklyCredits").value(6));
    }

    @Test
    @DisplayName("POST /api/auth/verify-email with missing token returns HTTP 400 Bad Request")
    void shouldReturnBadRequestWhenTokenMissingInPost() throws Exception {
        String jsonBody = """
                {
                    "token": ""
                }
                """;

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Verification token is required."));
    }

    @Test
    @DisplayName("POST /api/auth/verify-email with unknown token returns HTTP 404 Not Found")
    void shouldReturnNotFoundWhenTokenDoesNotExist() throws Exception {
        when(registrationService.verifyEmail("unknown-token")).thenThrow(new NoSuchElementException("Invalid or unknown verification token."));

        String jsonBody = """
                {
                    "token": "unknown-token"
                }
                """;

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Invalid or unknown verification token."));
    }

    @Test
    @DisplayName("POST /api/auth/verify-email with expired token returns HTTP 409 Conflict")
    void shouldReturnConflictWhenTokenExpired() throws Exception {
        when(registrationService.verifyEmail("expired-token")).thenThrow(new IllegalStateException("Verification token has expired."));

        String jsonBody = """
                {
                    "token": "expired-token"
                }
                """;

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Verification token has expired."));
    }

    @Test
    @DisplayName("GET /api/auth/verify-email?token=... with valid token returns HTTP 200 OK")
    void shouldVerifyEmailViaGetSuccessfully() throws Exception {
        VerifyEmailResponseDto responseDto = new VerifyEmailResponseDto(
                2L,
                "Student Two",
                "student@vitapstudent.ac.in",
                true,
                6,
                "Email successfully verified. Account activated with 6 weekly credits."
        );

        when(registrationService.verifyEmail("token-query-param")).thenReturn(responseDto);

        mockMvc.perform(get("/api/auth/verify-email").param("token", "token-query-param"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(2))
                .andExpect(jsonPath("$.fullName").value("Student Two"))
                .andExpect(jsonPath("$.universityEmail").value("student@vitapstudent.ac.in"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.weeklyCredits").value(6));
    }

    @Test
    @DisplayName("GET /api/auth/verify-email without token param returns HTTP 400 Bad Request")
    void shouldReturnBadRequestWhenGetTokenMissing() throws Exception {
        mockMvc.perform(get("/api/auth/verify-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Verification token query parameter is required."));
    }

    @Test
    @DisplayName("POST /api/auth/login with valid credentials returns HTTP 200 OK and JWT token")
    void shouldLoginSuccessfully() throws Exception {
        LoginResponseDto responseDto = new LoginResponseDto(
                "mock.jwt.token",
                "Bearer",
                86400L,
                1L,
                "Hamsa Hrudai",
                "hamsa@vitapstudent.ac.in",
                "23MIC7333",
                Role.STUDENT,
                6,
                100.0
        );

        when(authenticationService.login(any(LoginRequestDto.class))).thenReturn(responseDto);

        String json = """
                {
                    "identifier": "hamsa@vitapstudent.ac.in",
                    "password": "ValidPassword123!"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.fullName").value("Hamsa Hrudai"))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    @DisplayName("POST /api/auth/login with invalid password returns HTTP 401 Unauthorized")
    void shouldReturn401OnBadCredentials() throws Exception {
        when(authenticationService.login(any(LoginRequestDto.class)))
                .thenThrow(new BadCredentialsException("Invalid university email/admission number or password."));

        String json = """
                {
                    "identifier": "hamsa@vitapstudent.ac.in",
                    "password": "WrongPassword!"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid university email/admission number or password."));
    }

    @Test
    @DisplayName("POST /api/auth/login with unverified email returns HTTP 403 Forbidden")
    void shouldReturn403WhenEmailNotVerified() throws Exception {
        when(authenticationService.login(any(LoginRequestDto.class)))
                .thenThrow(new IllegalStateException("University email is not verified. Please verify your email before logging in."));

        String json = """
                {
                    "identifier": "unverified@vitapstudent.ac.in",
                    "password": "ValidPassword123!"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("University email is not verified. Please verify your email before logging in."));
    }
}
