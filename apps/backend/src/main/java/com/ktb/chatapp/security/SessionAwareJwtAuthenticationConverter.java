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

    private final JwtDenyListService denyListService;
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
