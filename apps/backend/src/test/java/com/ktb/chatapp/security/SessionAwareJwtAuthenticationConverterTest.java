package com.ktb.chatapp.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ktb.chatapp.exception.SessionExpiredException;
import com.ktb.chatapp.service.JwtDenyListService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionAwareJwtAuthenticationConverter 테스트")
class SessionAwareJwtAuthenticationConverterTest {

    @Mock
    private JwtDenyListService denyListService;

    @InjectMocks
    private SessionAwareJwtAuthenticationConverter converter;

    private Jwt validJwt;
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_USER_ID = "user-123";
    private static final String TEST_JTI = UUID.randomUUID().toString();
    private static final Long TEST_SESSION_VERSION = 1L;

    @BeforeEach
    void setUp() {
        validJwt = createJwt(TEST_EMAIL, TEST_USER_ID, TEST_JTI, TEST_SESSION_VERSION);
    }

    @Test
    @DisplayName("유효한 JWT로 인증 토큰 생성 성공")
    void convert_ValidJwtAndSession_Success() {
        // Given
        when(denyListService.isTokenDenied(TEST_JTI)).thenReturn(false);
        when(denyListService.isSessionVersionValid(TEST_USER_ID, TEST_SESSION_VERSION)).thenReturn(true);

        // When
        AbstractAuthenticationToken result = converter.convert(validJwt);

        // Then
        assertNotNull(result);
        assertEquals(TEST_EMAIL, result.getName());
        assertNotNull(result.getAuthorities());

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) result.getDetails();
        assertEquals(TEST_USER_ID, details.get("userId"));
        assertEquals(TEST_EMAIL, details.get("email"));
        assertEquals(TEST_JTI, details.get("jti"));

        verify(denyListService, times(1)).isTokenDenied(TEST_JTI);
        verify(denyListService, times(1)).isSessionVersionValid(TEST_USER_ID, TEST_SESSION_VERSION);
    }

    @Test
    @DisplayName("JWT에 userId가 없으면 SessionExpiredException 발생")
    void convert_MissingUserId_ThrowsSessionExpiredException() {
        // Given
        Jwt jwtWithoutUserId = createJwt(TEST_EMAIL, null, TEST_JTI, TEST_SESSION_VERSION);

        // When & Then
        SessionExpiredException exception = assertThrows(
            SessionExpiredException.class,
            () -> converter.convert(jwtWithoutUserId)
        );

        assertTrue(exception.getMessage().contains("Missing userId in JWT"));
        verify(denyListService, never()).isTokenDenied(anyString());
    }

    @Test
    @DisplayName("JWT에 JTI가 없으면 SessionExpiredException 발생")
    void convert_MissingJti_ThrowsSessionExpiredException() {
        // Given
        Jwt jwtWithoutJti = createJwt(TEST_EMAIL, TEST_USER_ID, null, TEST_SESSION_VERSION);

        // When & Then
        SessionExpiredException exception = assertThrows(
            SessionExpiredException.class,
            () -> converter.convert(jwtWithoutJti)
        );

        assertTrue(exception.getMessage().contains("Missing JTI in JWT"));
        verify(denyListService, never()).isTokenDenied(anyString());
    }

    @Test
    @DisplayName("토큰이 DenyList에 있으면 SessionExpiredException 발생")
    void convert_TokenDenied_ThrowsSessionExpiredException() {
        // Given
        when(denyListService.isTokenDenied(TEST_JTI)).thenReturn(true);

        // When & Then
        SessionExpiredException exception = assertThrows(
            SessionExpiredException.class,
            () -> converter.convert(validJwt)
        );

        assertEquals("Token has been revoked", exception.getMessage());
        verify(denyListService, times(1)).isTokenDenied(TEST_JTI);
    }

    @Test
    @DisplayName("Session Version이 없으면 SessionExpiredException 발생")
    void convert_MissingSessionVersion_ThrowsSessionExpiredException() {
        // Given
        Jwt jwtWithoutSessionVersion = createJwt(TEST_EMAIL, TEST_USER_ID, TEST_JTI, null);
        when(denyListService.isTokenDenied(TEST_JTI)).thenReturn(false);

        // When & Then
        SessionExpiredException exception = assertThrows(
            SessionExpiredException.class,
            () -> converter.convert(jwtWithoutSessionVersion)
        );

        assertTrue(exception.getMessage().contains("Missing sessionVersion in JWT"));
    }

    @Test
    @DisplayName("Session Version이 유효하지 않으면 SessionExpiredException 발생")
    void convert_InvalidSessionVersion_ThrowsSessionExpiredException() {
        // Given
        when(denyListService.isTokenDenied(TEST_JTI)).thenReturn(false);
        when(denyListService.isSessionVersionValid(TEST_USER_ID, TEST_SESSION_VERSION)).thenReturn(false);

        // When & Then
        SessionExpiredException exception = assertThrows(
            SessionExpiredException.class,
            () -> converter.convert(validJwt)
        );

        assertEquals("Session has been invalidated", exception.getMessage());
        verify(denyListService, times(1)).isSessionVersionValid(TEST_USER_ID, TEST_SESSION_VERSION);
    }

    @Test
    @DisplayName("유효한 JWT - Authorities가 올바르게 변환됨")
    void convert_ValidJwt_AuthoritiesConverted() {
        // Given
        when(denyListService.isTokenDenied(TEST_JTI)).thenReturn(false);
        when(denyListService.isSessionVersionValid(TEST_USER_ID, TEST_SESSION_VERSION)).thenReturn(true);

        // When
        AbstractAuthenticationToken result = converter.convert(validJwt);

        // Then
        assertNotNull(result.getAuthorities());
        assertTrue(result.getAuthorities() instanceof java.util.Collection);
    }

    @Test
    @DisplayName("여러 토큰 검증 요청이 각각 독립적으로 처리됨")
    void convert_MultipleRequests_IndependentValidation() {
        // Given
        String jti1 = UUID.randomUUID().toString();
        String jti2 = UUID.randomUUID().toString();

        Jwt jwt1 = createJwt("user1@test.com", "user-1", jti1, 1L);
        Jwt jwt2 = createJwt("user2@test.com", "user-2", jti2, 1L);

        when(denyListService.isTokenDenied(jti1)).thenReturn(false);
        when(denyListService.isTokenDenied(jti2)).thenReturn(false);
        when(denyListService.isSessionVersionValid("user-1", 1L)).thenReturn(true);
        when(denyListService.isSessionVersionValid("user-2", 1L)).thenReturn(true);

        // When
        AbstractAuthenticationToken result1 = converter.convert(jwt1);
        AbstractAuthenticationToken result2 = converter.convert(jwt2);

        // Then
        assertEquals("user1@test.com", result1.getName());
        assertEquals("user2@test.com", result2.getName());

        verify(denyListService, times(1)).isTokenDenied(jti1);
        verify(denyListService, times(1)).isTokenDenied(jti2);
        verify(denyListService, times(1)).isSessionVersionValid("user-1", 1L);
        verify(denyListService, times(1)).isSessionVersionValid("user-2", 1L);
    }

    @Test
    @DisplayName("JWT에 subject(email)가 null일 때 처리")
    void convert_NullSubject_HandlesGracefully() {
        // Given
        Jwt jwtWithNullSubject = createJwt(null, TEST_USER_ID, TEST_JTI, TEST_SESSION_VERSION);
        when(denyListService.isTokenDenied(TEST_JTI)).thenReturn(false);
        when(denyListService.isSessionVersionValid(TEST_USER_ID, TEST_SESSION_VERSION)).thenReturn(true);

        // When
        AbstractAuthenticationToken result = converter.convert(jwtWithNullSubject);

        // Then
        assertNotNull(result);
        assertNull(result.getName());
    }

    // Helper methods
    private Jwt createJwt(String subject, String userId, String jti, Long sessionVersion) {
        Map<String, Object> claims = new HashMap<>();
        if (userId != null) {
            claims.put("userId", userId);
        }
        if (sessionVersion != null) {
            claims.put("sessionVersion", sessionVersion);
        }

        Instant now = Instant.now();
        return new Jwt(
            "test-token-value",
            now,
            now.plusSeconds(3600),
            Map.of("alg", "HS256", "typ", "JWT"),
            claims
        ) {
            @Override
            public String getSubject() {
                return subject;
            }

            @Override
            public String getId() {
                return jti;
            }
        };
    }
}
