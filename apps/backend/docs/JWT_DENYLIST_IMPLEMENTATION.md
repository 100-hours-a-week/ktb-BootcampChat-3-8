# JWT DenyList 구현 가이드

## 목차
1. [Phase 1: JwtDenyListService 구현](#phase-1-jwtdenylistservice-구현)
2. [Phase 2: JWT 및 인증 플로우 수정](#phase-2-jwt-및-인증-플로우-수정)
3. [Phase 3: HTTP-Only Cookie 전환](#phase-3-http-only-cookie-전환)
4. [Phase 4: 기존 Session 코드 제거](#phase-4-기존-session-코드-제거)
5. [테스트 시나리오](#테스트-시나리오)

---

## Phase 1: JwtDenyListService 구현

### 1.1 JwtDenyListService.java (신규 작성)

**파일 위치:** `src/main/java/com/ktb/chatapp/service/JwtDenyListService.java`

```java
package com.ktb.chatapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * JWT DenyList 관리 서비스
 * Redis를 사용하여 무효화된 토큰과 세션 버전을 관리합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JwtDenyListService {

    private final RedisTemplate<String, String> redisTemplate;

    // Redis Key Prefixes
    private static final String DENYLIST_PREFIX = "jwt:denylist:";
    private static final String SESSION_VERSION_PREFIX = "jwt:session:version:";

    /**
     * 토큰을 DenyList에 추가 (로그아웃 시)
     *
     * @param jti JWT ID (고유 식별자)
     * @param expiresAt 토큰 만료 시간
     */
    public void denyToken(String jti, Instant expiresAt) {
        if (jti == null || expiresAt == null) {
            log.warn("Cannot deny token: jti or expiresAt is null");
            return;
        }

        // 이미 만료된 토큰은 추가하지 않음
        long remainingSeconds = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        if (remainingSeconds <= 0) {
            log.debug("Token already expired, not adding to denylist: {}", jti);
            return;
        }

        try {
            String key = DENYLIST_PREFIX + jti;
            redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(remainingSeconds));
            log.info("Token added to denylist: {} (TTL: {}s)", jti, remainingSeconds);
        } catch (Exception e) {
            log.error("Failed to add token to denylist: {}", jti, e);
            throw new RuntimeException("DenyList 추가 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 토큰이 DenyList에 있는지 확인
     *
     * @param jti JWT ID
     * @return true if denied, false otherwise
     */
    public boolean isTokenDenied(String jti) {
        if (jti == null) {
            log.warn("Cannot check denylist: jti is null");
            return false;
        }

        try {
            String key = DENYLIST_PREFIX + jti;
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("Failed to check denylist for token: {}", jti, e);
            // Redis 장애 시 보수적으로 거부하지 않음 (가용성 우선)
            return false;
        }
    }

    /**
     * 사용자의 세션 버전을 증가 (기존 토큰 모두 무효화)
     * 단일 세션 정책 구현용
     *
     * @param userId 사용자 ID
     * @return 새로운 세션 버전
     */
    public long incrementSessionVersion(String userId) {
        if (userId == null) {
            log.warn("Cannot increment session version: userId is null");
            return 0L;
        }

        try {
            String key = SESSION_VERSION_PREFIX + userId;
            Long newVersion = redisTemplate.opsForValue().increment(key);

            if (newVersion == null) {
                newVersion = 1L;
            }

            // TTL 설정 (JWT 최대 만료시간보다 길게)
            redisTemplate.expire(key, Duration.ofDays(30));

            log.info("Session version incremented for user {}: {}", userId, newVersion);
            return newVersion;
        } catch (Exception e) {
            log.error("Failed to increment session version for user: {}", userId, e);
            throw new RuntimeException("세션 버전 증가 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 사용자의 현재 세션 버전 조회
     *
     * @param userId 사용자 ID
     * @return 현재 세션 버전 (없으면 0)
     */
    public long getCurrentSessionVersion(String userId) {
        if (userId == null) {
            log.warn("Cannot get session version: userId is null");
            return 0L;
        }

        try {
            String key = SESSION_VERSION_PREFIX + userId;
            String version = redisTemplate.opsForValue().get(key);
            return version != null ? Long.parseLong(version) : 0L;
        } catch (Exception e) {
            log.error("Failed to get session version for user: {}", userId, e);
            return 0L;
        }
    }

    /**
     * 세션 버전 검증
     *
     * @param userId 사용자 ID
     * @param tokenSessionVersion 토큰의 세션 버전
     * @return true if valid, false otherwise
     */
    public boolean isSessionVersionValid(String userId, Long tokenSessionVersion) {
        if (userId == null || tokenSessionVersion == null) {
            log.warn("Cannot validate session version: userId or tokenSessionVersion is null");
            return false;
        }

        try {
            long currentVersion = getCurrentSessionVersion(userId);

            // 버전이 설정되지 않았으면 모든 토큰 허용
            if (currentVersion == 0L) {
                return true;
            }

            // 토큰의 버전이 현재 버전과 같거나 커야 함
            boolean valid = tokenSessionVersion >= currentVersion;

            if (!valid) {
                log.warn("Session version mismatch for user {}: token={}, current={}",
                    userId, tokenSessionVersion, currentVersion);
            }

            return valid;
        } catch (Exception e) {
            log.error("Failed to validate session version for user: {}", userId, e);
            // Redis 장애 시 보수적으로 허용 (가용성 우선)
            return true;
        }
    }

    /**
     * 사용자의 모든 세션 정보 삭제 (관리자용)
     *
     * @param userId 사용자 ID
     */
    public void clearUserSessions(String userId) {
        if (userId == null) {
            log.warn("Cannot clear sessions: userId is null");
            return;
        }

        try {
            String key = SESSION_VERSION_PREFIX + userId;
            redisTemplate.delete(key);
            log.info("Cleared all session data for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to clear sessions for user: {}", userId, e);
        }
    }
}
```

### 1.2 Redis Configuration 확인

**파일 위치:** `src/main/java/com/ktb/chatapp/config/RedisConfig.java`

이미 Redisson을 사용 중이므로, RedisTemplate Bean이 있는지 확인하고 없으면 추가:

```java
package com.ktb.chatapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key와 Value 모두 String Serializer 사용
        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }
}
```

---

## Phase 2: JWT 및 인증 플로우 수정

### 2.1 JwtService.java 수정

**변경 사항:**
1. JWT에 JTI (JWT ID) 추가
2. JWT에 sessionVersion 추가
3. JTI 추출 메서드 추가

```java
package com.ktb.chatapp.service;

import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final JwtDecoder expiredTokenDecoder;
    private final JwtDenyListService denyListService;  // 추가

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    public JwtService(
            JwtEncoder jwtEncoder,
            JwtDecoder jwtDecoder,
            @Qualifier("expiredTokenDecoder") JwtDecoder expiredTokenDecoder,
            JwtDenyListService denyListService) {  // 추가
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.expiredTokenDecoder = expiredTokenDecoder;
        this.denyListService = denyListService;
    }

    /**
     * JWT 토큰 생성 (수정됨)
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
     * 만료된 토큰에서 JTI 추출 (신규)
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
     * 만료된 토큰에서 만료 시간 추출 (신규)
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
```

### 2.2 SessionAwareJwtAuthenticationConverter.java 수정

**변경 사항:** DenyList 및 SessionVersion 검증 추가

```java
package com.ktb.chatapp.security;

import com.ktb.chatapp.exception.SessionExpiredException;
import com.ktb.chatapp.service.JwtDenyListService;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SessionAwareJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtDenyListService denyListService;  // SessionService 대신 DenyListService 사용
    private final JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // 1. JWT에서 사용자 이메일 및 ID 추출
        String email = jwt.getSubject();
        String userId = jwt.getClaimAsString("userId");
        String jti = jwt.getId();
        Long sessionVersion = jwt.getClaim("sessionVersion");

        // 2. userId 유효성 검증
        if (userId == null) {
            log.warn("JWT missing userId claim for email: {}", email);
            throw new SessionExpiredException("Missing userId in JWT");
        }

        // 3. JTI 유효성 검증
        if (jti == null) {
            log.warn("JWT missing JTI claim for user: {}", userId);
            throw new SessionExpiredException("Missing JTI in JWT");
        }

        // 4. DenyList 확인
        if (denyListService.isTokenDenied(jti)) {
            log.warn("Token is in denylist: {}", jti);
            throw new SessionExpiredException("Token has been revoked");
        }

        // 5. Session Version 확인 (단일 세션 정책)
        if (sessionVersion == null) {
            log.warn("JWT missing sessionVersion claim for user: {}", userId);
            throw new SessionExpiredException("Missing sessionVersion in JWT");
        }

        if (!denyListService.isSessionVersionValid(userId, sessionVersion)) {
            log.warn("Session version mismatch for user: {}", userId);
            throw new SessionExpiredException("Session has been invalidated");
        }

        // 6. Authorities 생성
        Collection<GrantedAuthority> authorities = jwtGrantedAuthoritiesConverter.convert(jwt);

        // 7. JwtAuthenticationToken 생성 with details
        JwtAuthenticationToken authenticationToken = new JwtAuthenticationToken(jwt, authorities, email);

        // 8. Details에 userId 포함 (sessionId는 제거)
        Map<String, Object> details = new HashMap<>();
        details.put("userId", userId);
        details.put("email", email);
        details.put("jti", jti);
        authenticationToken.setDetails(details);

        log.debug("JWT authentication successful for user: {} (email: {})", userId, email);

        return authenticationToken;
    }
}
```

### 2.3 AuthController.java 수정

**변경 사항:**
1. 로그인 시 SessionVersion 증가 (단일 세션 정책)
2. 로그아웃 시 DenyList 추가
3. SessionService 제거

**파일:** `src/main/java/com/ktb/chatapp/controller/AuthController.java`

주요 수정 부분만 표시:

```java
package com.ktb.chatapp.controller;

// ... imports ...
import com.ktb.chatapp.service.JwtDenyListService;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtDenyListService denyListService;  // SessionService 대신
    private final ApplicationEventPublisher eventPublisher;

    // ... 기존 메서드들 ...

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest loginRequest,
            BindingResult bindingResult,
            HttpServletRequest request) {

        ResponseEntity<?> errors = getBindingError(bindingResult);
        if (errors != null) return errors;

        try {
            User user = userRepository.findByEmail(loginRequest.getEmail().toLowerCase())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            user.getEmail(),
                            loginRequest.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 단일 세션 정책: 세션 버전 증가 (기존 토큰 모두 무효화)
            denyListService.incrementSessionVersion(user.getId());

            // Generate JWT token (sessionVersion 자동 포함됨)
            String token = jwtService.generateToken(user.getEmail(), user.getId());

            LoginResponse response = LoginResponse.builder()
                    .success(true)
                    .token(token)
                    .sessionId(null)  // sessionId 제거
                    .user(new AuthUserDto(user.getId(), user.getName(), user.getEmail(), user.getProfileImage()))
                    .build();

            return ResponseEntity.ok()
                    .header("Authorization", "Bearer " + token)
                    .body(response);

        } catch (UsernameNotFoundException | BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(StandardResponse.error("이메일 또는 비밀번호가 올바르지 않습니다."));
        } catch (Exception e) {
            log.error("Login error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StandardResponse.error("로그인 처리 중 오류가 발생했습니다."));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<StandardResponse<Void>> logout(Authentication authentication) {
        try {
            if (authentication == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(StandardResponse.error("인증이 필요합니다."));
            }

            // JWT에서 정보 추출
            Jwt jwt = (Jwt) authentication.getCredentials();
            String jti = jwt.getId();
            String userId = jwt.getClaimAsString("userId");

            if (jti != null && userId != null) {
                // DenyList에 추가 (TTL: 토큰 만료 시간)
                denyListService.denyToken(jti, jwt.getExpiresAt());

                // Publish event for session ended
                eventPublisher.publishEvent(new SessionEndedEvent(
                        this, userId, "logout", "로그아웃되었습니다."
                ));
            }

            SecurityContextHolder.clearContext();

            return ResponseEntity.ok(StandardResponse.success("로그아웃이 완료되었습니다.", null));

        } catch (Exception e) {
            log.error("Logout error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StandardResponse.error("로그아웃 처리 중 오류가 발생했습니다."));
        }
    }

    @PostMapping("/verify-token")
    public ResponseEntity<?> verifyToken(HttpServletRequest request) {
        try {
            String token = extractToken(request);

            if (token == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new TokenVerifyResponse(false, "토큰이 필요합니다.", null));
            }

            // 토큰 유효성 검증 (DenyList 및 SessionVersion 자동 확인)
            if (!jwtService.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new TokenVerifyResponse(false, "유효하지 않은 토큰입니다.", null));
            }

            // 토큰에서 사용자 정보 추출
            String userId = jwtService.extractUserId(token);
            Optional<User> userOpt = userRepository.findById(userId);

            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new TokenVerifyResponse(false, "사용자를 찾을 수 없습니다.", null));
            }

            User user = userOpt.get();
            AuthUserDto authUserDto = new AuthUserDto(user.getId(), user.getName(), user.getEmail(), user.getProfileImage());
            return ResponseEntity.ok(new TokenVerifyResponse(true, "토큰이 유효합니다.", authUserDto));

        } catch (Exception e) {
            log.error("Token verification error: ", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new TokenVerifyResponse(false, "토큰 검증 중 오류가 발생했습니다.", null));
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(HttpServletRequest request) {
        try {
            String token = extractToken(request);

            if (token == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new TokenRefreshResponse(false, "토큰이 필요합니다.", null, null));
            }

            // 만료된 토큰에서 정보 추출
            String userId = jwtService.extractUserIdFromExpiredToken(token);
            String oldJti = jwtService.extractJtiFromExpiredToken(token);
            Instant oldExpiration = jwtService.extractExpirationFromExpiredToken(token);

            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new TokenRefreshResponse(false, "사용자를 찾을 수 없습니다.", null, null));
            }

            User user = userOpt.get();

            // 기존 토큰을 DenyList에 추가 (리플레이 공격 방지)
            if (oldJti != null && oldExpiration != null) {
                denyListService.denyToken(oldJti, oldExpiration);
            }

            // 새로운 토큰 생성
            String newToken = jwtService.generateToken(user.getEmail(), user.getId());

            return ResponseEntity.ok(
                new TokenRefreshResponse(true, "토큰이 갱신되었습니다.", newToken, null)
            );

        } catch (Exception e) {
            log.error("Token refresh error: ", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new TokenRefreshResponse(false, "토큰 갱신 중 오류가 발생했습니다.", null, null));
        }
    }

    // extractToken 메서드는 그대로 유지
    private String extractToken(HttpServletRequest request) {
        String token = request.getHeader("x-auth-token");
        if (token != null && !token.isEmpty()) {
            return token;
        }
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
```

---

## Phase 3: HTTP-Only Cookie 전환

### 3.1 Cookie 설정 유틸리티 작성

**파일 위치:** `src/main/java/com/ktb/chatapp/util/CookieUtil.java`

```java
package com.ktb.chatapp.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

    @Value("${app.jwt.cookie-name:jwt}")
    private String cookieName;

    @Value("${app.jwt.cookie-max-age:3600}")
    private int cookieMaxAge;

    @Value("${app.jwt.cookie-domain:}")
    private String cookieDomain;

    @Value("${app.jwt.cookie-secure:true}")
    private boolean cookieSecure;

    /**
     * JWT를 HTTP-Only Cookie에 추가
     */
    public void addJwtCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(cookieName, token);
        cookie.setHttpOnly(true);  // JavaScript 접근 불가 (XSS 방어)
        cookie.setSecure(cookieSecure);  // HTTPS only
        cookie.setPath("/");
        cookie.setMaxAge(cookieMaxAge);
        cookie.setAttribute("SameSite", "Strict");  // CSRF 방어

        if (cookieDomain != null && !cookieDomain.isEmpty()) {
            cookie.setDomain(cookieDomain);
        }

        response.addCookie(cookie);
    }

    /**
     * JWT Cookie 제거 (로그아웃 시)
     */
    public void deleteJwtCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(cookieName, null);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/");
        cookie.setMaxAge(0);

        if (cookieDomain != null && !cookieDomain.isEmpty()) {
            cookie.setDomain(cookieDomain);
        }

        response.addCookie(cookie);
    }

    /**
     * Request에서 JWT Cookie 추출
     */
    public String getJwtFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
```

### 3.2 CustomBearerTokenResolver 수정

Cookie에서 JWT 추출하도록 수정:

```java
package com.ktb.chatapp.security;

import com.ktb.chatapp.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomBearerTokenResolver implements BearerTokenResolver {

    private final CookieUtil cookieUtil;

    @Override
    public String resolve(HttpServletRequest request) {
        // 1순위: HTTP-Only Cookie에서 추출
        String tokenFromCookie = cookieUtil.getJwtFromCookie(request);
        if (tokenFromCookie != null) {
            return tokenFromCookie;
        }

        // 2순위: Authorization 헤더 (하위 호환성)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // 3순위: x-auth-token 헤더 (하위 호환성)
        String customHeader = request.getHeader("x-auth-token");
        if (customHeader != null && !customHeader.isEmpty()) {
            return customHeader;
        }

        return null;
    }
}
```

### 3.3 AuthController에 Cookie 응답 추가

```java
// AuthController.java - login 메서드 수정
@PostMapping("/login")
public ResponseEntity<?> login(
        @Valid @RequestBody LoginRequest loginRequest,
        BindingResult bindingResult,
        HttpServletRequest request,
        HttpServletResponse response) {  // HttpServletResponse 추가

    // ... 기존 로직 ...

    // JWT 생성
    String token = jwtService.generateToken(user.getEmail(), user.getId());

    // HTTP-Only Cookie에 JWT 추가
    cookieUtil.addJwtCookie(response, token);

    LoginResponse loginResponse = LoginResponse.builder()
            .success(true)
            .token(token)  // 응답 body에도 포함 (선택사항)
            .user(new AuthUserDto(user.getId(), user.getName(), user.getEmail(), user.getProfileImage()))
            .build();

    return ResponseEntity.ok()
            .body(loginResponse);
}

// logout 메서드 수정
@PostMapping("/logout")
public ResponseEntity<StandardResponse<Void>> logout(
        Authentication authentication,
        HttpServletResponse response) {  // HttpServletResponse 추가

    // ... 기존 DenyList 로직 ...

    // Cookie 삭제
    cookieUtil.deleteJwtCookie(response);

    return ResponseEntity.ok(StandardResponse.success("로그아웃이 완료되었습니다.", null));
}
```

### 3.4 application.yml 설정 추가

```yaml
app:
  jwt:
    expiration-ms: 3600000  # 1시간
    cookie-name: jwt
    cookie-max-age: 3600  # 1시간 (초 단위)
    cookie-domain: ""  # 프로덕션에서는 도메인 설정
    cookie-secure: true  # HTTPS only
```

### 3.5 CORS 설정 업데이트

Cookie 사용을 위해 `allowCredentials: true` 필요:

```java
// SecurityConfig.java - createCorsConfiguration 메서드 수정
private CorsConfiguration createCorsConfiguration() {
    CorsConfiguration config = new CorsConfiguration();

    // "*" 대신 구체적인 Origin 지정 (credentials: true와 함께 사용 필수)
    config.setAllowedOriginPatterns(List.of(
        "http://localhost:3000",
        "http://localhost:5173",
        "https://yourdomain.com"
    ));

    config.setAllowedMethods(CORS_ALLOWED_METHODS);
    config.setAllowedHeaders(CORS_ALLOWED_HEADERS);
    config.setExposedHeaders(CORS_EXPOSED_HEADERS);
    config.setAllowCredentials(true);  // Cookie 사용 허용
    config.setMaxAge(Duration.ofHours(1).getSeconds());
    return config;
}
```

---

## Phase 4: 기존 Session 코드 제거

### 4.1 제거할 파일 목록

```bash
# 삭제할 파일들
src/main/java/com/ktb/chatapp/service/SessionService.java
src/main/java/com/ktb/chatapp/service/SessionData.java
src/main/java/com/ktb/chatapp/service/SessionMetadata.java
src/main/java/com/ktb/chatapp/service/SessionCreationResult.java
src/main/java/com/ktb/chatapp/service/SessionValidationResult.java
src/main/java/com/ktb/chatapp/service/session/SessionStore.java
src/main/java/com/ktb/chatapp/service/session/SessionMongoStore.java
src/main/java/com/ktb/chatapp/model/Session.java
src/main/java/com/ktb/chatapp/repository/SessionRepository.java

# 관련 테스트 파일도 삭제
src/test/java/com/ktb/chatapp/service/SessionServiceTest.java
```

### 4.2 DTO 수정

**LoginResponse.java** - sessionId 필드 제거:

```java
@Data
@Builder
public class LoginResponse {
    private boolean success;
    private String message;
    private String token;
    // private String sessionId;  // 제거
    private AuthUserDto user;
}

@Data
public class TokenRefreshResponse {
    private boolean success;
    private String message;
    private String token;
    // private String sessionId;  // 제거
}
```

---

## 테스트 시나리오

### 5.1 단위 테스트

**JwtDenyListServiceTest.java:**

```java
package com.ktb.chatapp.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class JwtDenyListServiceTest {

    @Autowired
    private JwtDenyListService denyListService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Test
    void testDenyToken() {
        String jti = "test-jti-123";
        Instant expiresAt = Instant.now().plusSeconds(3600);

        // DenyList에 추가
        denyListService.denyToken(jti, expiresAt);

        // 확인
        assertThat(denyListService.isTokenDenied(jti)).isTrue();
    }

    @Test
    void testSessionVersion() {
        String userId = "user-123";

        // 초기 버전
        long version1 = denyListService.getCurrentSessionVersion(userId);

        // 버전 증가
        long version2 = denyListService.incrementSessionVersion(userId);

        // 검증
        assertThat(version2).isGreaterThan(version1);
        assertThat(denyListService.isSessionVersionValid(userId, version2)).isTrue();
        assertThat(denyListService.isSessionVersionValid(userId, version1)).isFalse();
    }
}
```

### 5.2 통합 테스트 시나리오

1. **로그인 테스트**
   - 로그인 요청 → JWT 발급 확인
   - Cookie에 JWT 포함 확인
   - SessionVersion 증가 확인

2. **인증 테스트**
   - JWT로 API 요청 → 성공
   - DenyList 확인 로직 실행 확인

3. **로그아웃 테스트**
   - 로그아웃 → DenyList 추가 확인
   - 동일 JWT로 재요청 → 401 실패 확인
   - Cookie 삭제 확인

4. **단일 세션 정책 테스트**
   - 첫 로그인 → JWT1 발급
   - 두 번째 로그인 → JWT2 발급
   - JWT1로 요청 → 401 실패 (SessionVersion 불일치)
   - JWT2로 요청 → 200 성공

5. **토큰 갱신 테스트**
   - 만료된 JWT로 refresh 요청
   - 새 JWT 발급 확인
   - 기존 JWT는 DenyList 추가 확인

---

## 주의사항

1. **Redis 연결 확인**
   - Redisson 설정이 올바른지 확인
   - Redis 서버 접근 가능 여부 확인

2. **CORS 설정**
   - `allowCredentials: true`는 `allowedOrigins: "*"` 와 함께 사용 불가
   - 구체적인 도메인 명시 필요

3. **Cookie 설정**
   - 프로덕션에서는 `Secure: true` 필수 (HTTPS)
   - `SameSite: Strict` 설정으로 CSRF 방어

4. **하위 호환성**
   - 기존 클라이언트를 위해 Authorization 헤더도 지원
   - 점진적 마이그레이션 가능

5. **모니터링**
   - Redis 메모리 사용량 모니터링
   - DenyList 크기 추적
   - SessionVersion 증가 빈도 확인
