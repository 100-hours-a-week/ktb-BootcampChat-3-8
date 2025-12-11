package com.ktb.chatapp.websocket.socketio;

import com.corundumstudio.socketio.AuthTokenListener;
import com.corundumstudio.socketio.AuthTokenResult;
import com.corundumstudio.socketio.SocketIOClient;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.service.JwtDenyListService;
import com.ktb.chatapp.websocket.socketio.handler.ConnectionLoginHandler;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Socket.IO Authorization Handler
 * socket.handshake.auth.token을 JWT로 처리한다.
 * sessionId는 하위 호환성을 위해 optional로 처리
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AuthTokenListenerImpl implements AuthTokenListener {

    private final org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;
    private final JwtDenyListService denyListService;
    private final UserRepository userRepository;
    private final ObjectProvider<ConnectionLoginHandler> socketIOChatHandlerProvider;

    @Override
    public AuthTokenResult getAuthTokenResult(Object _authToken, SocketIOClient client) {
        try {
            var authToken = (Map<?, ?>) _authToken;
            String token = authToken.get("token") != null ? authToken.get("token").toString() : null;
            String sessionId = authToken.get("sessionId") != null ? authToken.get("sessionId").toString() : null;

            // token은 필수, sessionId는 optional
            if (token == null) {
                log.warn("Missing JWT token in Socket.IO handshake");
                return new AuthTokenResult(false, Map.of("message", "JWT token is required"));
            }

            // JWT 검증 및 정보 추출
            Jwt jwt;
            String userId;
            String jti;
            Long sessionVersion;

            try {
                // JWT 디코딩 (만료 검증 포함)
                jwt = jwtDecoder.decode(token);
                userId = jwt.getClaimAsString("userId");
                jti = jwt.getId();
                sessionVersion = jwt.getClaim("sessionVersion");

                if (userId == null) {
                    log.warn("JWT missing userId claim");
                    return new AuthTokenResult(false, Map.of("message", "Invalid JWT: missing userId"));
                }

                if (jti == null) {
                    log.warn("JWT missing JTI claim for user: {}", userId);
                    return new AuthTokenResult(false, Map.of("message", "Invalid JWT: missing JTI"));
                }

            } catch (JwtException e) {
                log.warn("Invalid JWT token: {}", e.getMessage());
                return new AuthTokenResult(false, Map.of("message", "Invalid or expired token"));
            }

            // DenyList 확인
            if (denyListService.isTokenDenied(jti)) {
                log.warn("Token is in denylist: {}", jti);
                return new AuthTokenResult(false, Map.of("message", "Token has been revoked"));
            }

            // SessionVersion 확인 (단일 세션 정책)
            if (sessionVersion != null && !denyListService.isSessionVersionValid(userId, sessionVersion)) {
                log.warn("Session version mismatch for user: {}", userId);
                return new AuthTokenResult(false, Map.of("message", "Session has been invalidated"));
            }

            // Load user from database
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                log.error("User not found: {}", userId);
                return new AuthTokenResult(false, Map.of("message", "User not found"));
            }

            log.info("Socket.IO connection authorized for user: {} ({}) - JTI: {}", user.getName(), userId, jti);

            // sessionId는 optional, JWT에서 추출한 정보로 SocketUser 생성
            var socketUser = new SocketUser(user.getId(), user.getName(), sessionId, client.getSessionId().toString());
            socketIOChatHandlerProvider.getObject().onConnect(client, socketUser);
            return AuthTokenResult.AuthTokenResultSuccess;
        } catch (Exception e) {
            log.error("Socket.IO authentication error: {}", e.getMessage(), e);
            return new AuthTokenResult(false, Map.of("message", "Authentication failed"));
        }
    }
}
