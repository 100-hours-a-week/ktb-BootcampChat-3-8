package com.ktb.chatapp.security;

import com.ktb.chatapp.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class CustomBearerTokenResolver implements BearerTokenResolver {
    private final CookieUtil cookieUtil;
    
    private static final String CUSTOM_HEADER = "x-auth-token";

    @Override
    public String resolve(HttpServletRequest request) {
        // 1. Try HTTP-Only Cookie extract
        String tokenFormCookie = cookieUtil.getJwtFromCookie(request);
        if (tokenFormCookie != null) {
            return  tokenFormCookie;
        }

        // 2. Authorization 헤더 (하위 호환성)
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // 3. x-auth-token 헤더 (하위 호환성)
        String customHeader = request.getHeader(CUSTOM_HEADER);
        if (customHeader != null && !customHeader.isEmpty()) {
            return customHeader;
        }
        
        // 4. Try query parameter (for WebSocket connections)
        String token = request.getParameter("token");
        if (StringUtils.hasText(token)) {
            return token;
        }

        return null;
    }
}
