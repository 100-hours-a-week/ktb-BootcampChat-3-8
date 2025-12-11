package com.ktb.chatapp.service;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtDenyListService {
    private final RedisTemplate redisTemplate;

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
            Object version = redisTemplate.opsForValue().get(key);

            switch (version) {
                case null -> {
                    return 0L;
                }

                // increment()로 저장된 Long 타입 처리
                case Long l -> {
                    return l;
                }

                // Integer로 저장된 경우
                case Integer i -> {
                    return i.longValue();
                }

                // String으로 저장된 경우
                case String s -> {
                    return Long.parseLong(s);
                }

                // 기타 Number 타입 처리
                case Number number -> {
                    return number.longValue();
                }
                default -> {
                    log.warn("Unexpected session version type for user {}: {}", userId, version.getClass());
                    return 0L;
                }
            }
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
