package edu.university.badminton.config;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import edu.university.badminton.model.Role;
import edu.university.badminton.model.Student;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Service managing standard RFC 7519 HMAC-SHA256 (HS256) JSON Web Token generation and validation.
 */
@Service
public class JwtService {

    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final String HEADER_B64 = Base64.getUrlEncoder().withoutPadding().encodeToString(HEADER_JSON.getBytes(StandardCharsets.UTF_8));

    private final JwtTokenProperties jwtProperties;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public JwtService(JwtTokenProperties jwtProperties, Clock clock, ObjectMapper objectMapper) {
        if (jwtProperties == null || jwtProperties.getSecret() == null || jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must be configured with at least 256 bits (32 bytes).");
        }
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    /**
     * Generates a signed JWT for the given student account.
     */
    public String generateToken(Student student) {
        if (student == null || student.getId() == null) {
            throw new IllegalArgumentException("Cannot generate token for null student or missing ID.");
        }

        Instant now = clock.instant();
        long iat = now.getEpochSecond();
        long exp = now.plusSeconds((long) jwtProperties.getExpiryHours() * 3600).getEpochSecond();

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", String.valueOf(student.getId()));
        claims.put("email", student.getUniversityEmail());
        claims.put("admissionNumber", student.getAdmissionNumber());
        claims.put("name", student.getFullName());
        claims.put("role", student.getRole() != null ? student.getRole().name() : Role.STUDENT.name());
        claims.put("iat", iat);
        claims.put("exp", exp);

        try {
            String payloadJson = objectMapper.writeValueAsString(claims);
            String payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            String dataToSign = HEADER_B64 + "." + payloadB64;
            String signatureB64 = sign(dataToSign, jwtProperties.getSecret());
            return dataToSign + "." + signatureB64;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate JWT token: " + e.getMessage(), e);
        }
    }

    /**
     * Cryptographically validates the token's signature, expiry, and structure, returning extracted claims.
     */
    public JwtClaims validateAndParseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("JWT token cannot be null or blank.");
        }

        String[] parts = token.trim().split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Malformed JWT: expected 3 dot-separated parts.");
        }

        String headerB64 = parts[0];
        String payloadB64 = parts[1];
        String signatureB64 = parts[2];

        // 1. Validate signature using constant-time comparison
        String dataToSign = headerB64 + "." + payloadB64;
        String expectedSignatureB64 = sign(dataToSign, jwtProperties.getSecret());

        if (!MessageDigest.isEqual(
                signatureB64.getBytes(StandardCharsets.UTF_8),
                expectedSignatureB64.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new IllegalArgumentException("Invalid JWT signature: token was tampered with or signed with an unknown key.");
        }

        // 2. Parse payload
        Map<String, Object> claimsMap;
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadB64);
            claimsMap = objectMapper.readValue(payloadBytes, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed JWT payload: cannot decode JSON claims.");
        }

        // 3. Verify expiration
        Number expNumber = (Number) claimsMap.get("exp");
        if (expNumber == null) {
            throw new IllegalArgumentException("JWT is missing expiration (exp) claim.");
        }
        long exp = expNumber.longValue();
        long now = clock.instant().getEpochSecond();
        if (now > exp) {
            throw new IllegalArgumentException("JWT token has expired at " + exp + " (current time: " + now + ").");
        }

        // 4. Extract claims
        String sub = (String) claimsMap.get("sub");
        String email = (String) claimsMap.get("email");
        String admissionNumber = (String) claimsMap.get("admissionNumber");
        String name = (String) claimsMap.get("name");
        String roleStr = (String) claimsMap.get("role");

        Long studentId = sub != null ? Long.parseLong(sub) : null;
        Role role = Role.STUDENT;
        if (roleStr != null) {
            try {
                role = Role.valueOf(roleStr);
            } catch (IllegalArgumentException ignored) {
                // Default to STUDENT if unrecognized
            }
        }

        return new JwtClaims(studentId, email, admissionNumber, name, role);
    }

    private String sign(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGORITHM);
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Cryptographic algorithm error signing JWT: " + e.getMessage(), e);
        }
    }
}
