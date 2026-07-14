package com.finance.advisor.user;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtService 单元测试：通过反射式构造注入固定 secret（>=32 字节，HS256）。
 */
class JwtServiceTest {

    /** 64 字符固定密钥，满足 HS256 至少 32 字节要求。 */
    private static final String SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final long EXPIRATION = 86400000L;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, EXPIRATION);
    }

    @Test
    void generateToken_returnsNonBlankToken() {
        String token = jwtService.generateToken(1L, "alice");
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void extractUserId_returnsOriginalLong() {
        Long userId = 12345L;
        String token = jwtService.generateToken(userId, "alice");

        Long extracted = jwtService.extractUserId(token);
        assertEquals(userId, extracted);
    }

    @Test
    void extractUsername_returnsOriginal() {
        String token = jwtService.generateToken(1L, "alice");
        assertEquals("alice", jwtService.extractUsername(token));
    }

    @Test
    void validateToken_validToken_returnsTrue() {
        String token = jwtService.generateToken(1L, "alice");
        assertTrue(jwtService.validateToken(token));
    }

    @Test
    void tamperedToken_throwsJwtException() {
        String token = jwtService.generateToken(1L, "alice");
        // 篡改签名末尾若干字符
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThrows(JwtException.class, () -> jwtService.extractUserId(tampered));
        assertFalse(jwtService.validateToken(tampered));
    }
}
