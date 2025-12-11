package com.ktb.chatapp.service;

import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

/**
 * Spring Security의 JwtEncoder/JwtDecoder를 사용하는 JWT 서비스
 * JwtUtil을 대체하는 표준화된 JWT 처리 서비스
 */
@Service
@Slf4j
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final JwtDecoder expiredTokenDecoder;
    private final JwtDenyListService denyListService;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    public JwtService(
            JwtEncoder jwtEncoder,
            JwtDecoder jwtDecoder,
            @Qualifier("expiredTokenDecoder") JwtDecoder expiredTokenDecoder,
            JwtDenyListService denyListService) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.expiredTokenDecoder = expiredTokenDecoder;
        this.denyListService = denyListService;
    }

    /**
     * JWT 토큰 생성
     * @param email 사용자 이메일 (subject)
     * @param userId 사용자 ID
     * @return 생성된 JWT 토큰
     */
    public String generateToken(String email, String userId) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(jwtExpirationMs);

        // 고유 JTI 생성
        String jti = UUID.randomUUID().toString();

        // 현재 세션 버전 조회
        long sessionVersion = denyListService.getCurrentSessionVersion(userId);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(jti)  // JTI 추가
                .subject(email)
                .issuedAt(now)
                .expiresAt(expiry)
                .claim("userId", userId)
                .claim("sessionVersion", sessionVersion)  // 세션 버전 추가
                .build();

        var defaultJwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(defaultJwsHeader, claims)).getTokenValue();
    }

    /**
     * 로그아웃 용 JWT 토큰 생성
     */
    public String expireTokenCookie() {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .expiresAt(Instant.now())
                .build();

        var defaultJwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(defaultJwsHeader, claims)).getTokenValue();
    }

    /**
     * 토큰 유효성 검증 (UserDetails 포함)
     */
    public Boolean validateToken(String token, UserDetails userDetails) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            String jti = jwt.getId();
            String userId = jwt.getClaimAsString("userId");
            Long sessionVersion = jwt.getClaim("sessionVersion");

            // DenyList 확인
            if (denyListService.isTokenDenied(jti)) {
                log.debug("Token is in denylist: {}", jti);
                return false;
            }

            // Session Version 확인
            if (!denyListService.isSessionVersionValid(userId, sessionVersion)) {
                log.debug("Session version invalid for user: {}", userId);
                return false;
            }

            return jwt.getSubject().equals(userDetails.getUsername()) &&
                    jwt.getExpiresAt() != null &&
                    jwt.getExpiresAt().isAfter(Instant.now());
        } catch (JwtException e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 토큰 유효성 검증 (단순 검증)
     */
    public Boolean validateToken(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            String jti = jwt.getId();
            String userId = jwt.getClaimAsString("userId");
            Long sessionVersion = jwt.getClaim("sessionVersion");

            // DenyList 확인
            if (denyListService.isTokenDenied(jti)) {
                log.debug("Token is in denylist: {}", jti);
                return false;
            }

            // Session Version 확인
            if (!denyListService.isSessionVersionValid(userId, sessionVersion)) {
                log.debug("Session version invalid for user: {}", userId);
                return false;
            }

            return jwt.getExpiresAt() != null && jwt.getExpiresAt().isAfter(Instant.now());
        } catch (JwtException e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 토큰에서 이메일(subject) 추출
     */
    public String extractEmail(String token) {
        try {
            return jwtDecoder.decode(token).getSubject();
        } catch (JwtException e) {
            log.error("Failed to extract email from token: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 토큰에서 사용자 ID 추출
     */
    public String extractUserId(String token) {
        try {
            return jwtDecoder.decode(token).getClaim("userId");
        } catch (JwtException e) {
            log.error("Failed to extract userId from token: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 토큰에서 JTI 추출 (신규)
     */
    public String extractJti(String token) {
        try {
            return jwtDecoder.decode(token).getId();
        } catch (JwtException e) {
            log.error("Failed to extract JTI from token: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 토큰에서 만료 시간 추출
     */
    public Instant extractExpiration(String token) {
        try {
            return jwtDecoder.decode(token).getExpiresAt();
        } catch (JwtException e) {
            log.error("Failed to extract expiration from token: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 만료된 토큰에서 사용자 ID 추출
     */
    public String extractUserIdFromExpiredToken(String token) {
        try {
            return expiredTokenDecoder.decode(token).getClaim("userId");
        } catch (JwtException e) {
            log.error("Failed to extract userId from expired token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 만료된 토큰에서 이메일 추출
     */
    public String extractEmailFromExpiredToken(String token) {
        try {
            return expiredTokenDecoder.decode(token).getSubject();
        } catch (JwtException e) {
            log.error("Failed to extract email from expired token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 만료된 토큰에서 JTI 추출
     */
    public String extractJtiFromExpiredToken(String token) {
        try {
            return expiredTokenDecoder.decode(token).getId();
        } catch (JwtException e) {
            log.error("Failed to extract JTI from expired token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 만료된 토큰에서 만료 시간 추출
     */
    public Instant extractExpirationFromExpiredToken(String token) {
        try {
            return expiredTokenDecoder.decode(token).getExpiresAt();
        } catch (JwtException e) {
            log.error("Failed to extract expiration from expired token: {}", e.getMessage());
            return null;
        }
    }
}
