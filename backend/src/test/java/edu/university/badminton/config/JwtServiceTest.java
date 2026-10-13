package edu.university.badminton.config;

import tools.jackson.databind.ObjectMapper;
import edu.university.badminton.model.Role;
import edu.university.badminton.model.Student;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final Instant BASE_TIME = Instant.parse("2026-10-15T10:00:00Z");

    private JwtTokenProperties properties;
    private ObjectMapper objectMapper;
    private JwtService jwtService;
    private Student testStudent;

    @BeforeEach
    void setUp() {
        properties = new JwtTokenProperties();
        properties.setSecret("test-secret-key-that-is-at-least-256-bits-long-for-testing-purposes");
        properties.setExpiryHours(24);

        objectMapper = new ObjectMapper();
        jwtService = new JwtService(properties, Clock.fixed(BASE_TIME, UTC), objectMapper);

        testStudent = new Student("Bob Marshal", "bob.marshal@vitapstudent.ac.in", "24STAFF001", Role.STAFF);
        testStudent.setId(42L);
    }

    @Test
    @DisplayName("Generates authentic JWT and successfully decodes claims")
    void shouldGenerateAndValidateTokenSuccessfully() {
        String token = jwtService.generateToken(testStudent);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);

        JwtClaims claims = jwtService.validateAndParseToken(token);
        assertThat(claims.studentId()).isEqualTo(42L);
        assertThat(claims.email()).isEqualTo("bob.marshal@vitapstudent.ac.in");
        assertThat(claims.admissionNumber()).isEqualTo("24STAFF001");
        assertThat(claims.fullName()).isEqualTo("Bob Marshal");
        assertThat(claims.role()).isEqualTo(Role.STAFF);
    }

    @Test
    @DisplayName("Tampered payload is strictly rejected by cryptographic signature check")
    void shouldRejectTamperedPayload() {
        String token = jwtService.generateToken(testStudent);
        String[] parts = token.split("\\.");

        // Modify a character in the base64 payload
        char lastChar = parts[1].charAt(parts[1].length() - 1);
        char replacedChar = lastChar == 'A' ? 'B' : 'A';
        String tamperedPayload = parts[1].substring(0, parts[1].length() - 1) + replacedChar;
        String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThatThrownBy(() -> jwtService.validateAndParseToken(tamperedToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JWT signature");
    }

    @Test
    @DisplayName("Tampered signature is strictly rejected")
    void shouldRejectTamperedSignature() {
        String token = jwtService.generateToken(testStudent);
        String[] parts = token.split("\\.");

        String tamperedSignature = parts[2].substring(0, parts[2].length() - 2) + "ZZ";
        String tamperedToken = parts[0] + "." + parts[1] + "." + tamperedSignature;

        assertThatThrownBy(() -> jwtService.validateAndParseToken(tamperedToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JWT signature");
    }

    @Test
    @DisplayName("Token verified with a different secret key is rejected")
    void shouldRejectTokenSignedWithDifferentSecret() {
        String token = jwtService.generateToken(testStudent);

        JwtTokenProperties otherProperties = new JwtTokenProperties();
        otherProperties.setSecret("completely-different-secret-key-that-is-at-least-256-bits-long!");
        JwtService otherService = new JwtService(otherProperties, Clock.fixed(BASE_TIME, UTC), objectMapper);

        assertThatThrownBy(() -> otherService.validateAndParseToken(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JWT signature");
    }

    @Test
    @DisplayName("Expired token is rejected when verified after expiration time")
    void shouldRejectExpiredToken() {
        String token = jwtService.generateToken(testStudent);

        // Advance clock by 25 hours (expiry is 24 hours)
        Instant futureTime = BASE_TIME.plusSeconds(25 * 3600);
        JwtService futureService = new JwtService(properties, Clock.fixed(futureTime, UTC), objectMapper);

        assertThatThrownBy(() -> futureService.validateAndParseToken(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }
}
