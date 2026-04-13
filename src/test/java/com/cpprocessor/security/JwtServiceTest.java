package com.cpprocessor.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret",
                "myTestSecretKeyWhichIsAtLeast256BitsLongForHS256AlgorithmSecurity");
        ReflectionTestUtils.setField(jwtService, "expirationMs", 86400000L);
    }

    @Test
    void generateToken_ReturnsNonEmptyToken() {
        String token = jwtService.generateToken("testuser", "ROLE_USER");

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void extractUsername_ReturnsCorrectUsername() {
        String token = jwtService.generateToken("admin", "ROLE_ADMIN");

        String username = jwtService.extractUsername(token);

        assertEquals("admin", username);
    }

    @Test
    void isTokenValid_ValidToken_ReturnsTrue() {
        String token = jwtService.generateToken("testuser", "ROLE_USER");

        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void isTokenValid_ExpiredToken_ReturnsFalse() {
        ReflectionTestUtils.setField(jwtService, "expirationMs", -1000L);
        String token = jwtService.generateToken("testuser", "ROLE_USER");

        assertFalse(jwtService.isTokenValid(token));
    }

    @Test
    void isTokenValid_TamperedToken_ReturnsFalse() {
        String token = jwtService.generateToken("testuser", "ROLE_USER");
        String tamperedToken = token + "tampered";

        assertFalse(jwtService.isTokenValid(tamperedToken));
    }
}
