package com.ktb.chatapp.event;

import com.ktb.chatapp.config.CacheConfig;
import com.ktb.chatapp.dto.UserResponse;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 사용자 캐시 이벤트 리스너
 * 도메인 이벤트를 구독하여 캐시 관련 작업을 처리합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserCacheEventListener {

    private final UserRepository userRepository;

    /**
     * 로그인 이벤트 처리 - 캐시 Pre-warming
     */
    @EventListener
    @CachePut(value = CacheConfig.USER_PROFILE_CACHE, key = "#event.email")
    public UserResponse handleUserLogin(UserLoginEvent event) {
        log.debug("UserLoginEvent received - Caching user profile: {}", event.getEmail());

        User user = userRepository.findById(event.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found: " + event.getUserId()));

        return UserResponse.from(user);
    }

    /**
     * 캐시 무효화 이벤트 처리 (비동기)
     * email과 userId 모두 캐시 무효화
     */
    @Async
    @EventListener
    public void handleCacheEvict(UserCacheEvictEvent event) {
        log.debug("UserCacheEvictEvent received - Reason: {}, UserId: {}, Email: {}",
                event.getReason(), event.getUserId(), event.getEmail());

        String email = event.getEmail();
        String userId = event.getUserId();

        // email이 없으면 userId로 조회
        if (email == null && userId != null) {
            email = userRepository.findById(userId)
                    .map(User::getEmail)
                    .orElse(null);
        }

        // userId가 없으면 email로 조회
        if (userId == null && email != null) {
            userId = userRepository.findByEmail(email)
                    .map(User::getId)
                    .orElse(null);
        }

        // email 기반 캐시 무효화
        if (email != null) {
            evictUserCacheByEmail(email);
            log.info("User cache evicted by email - Email: {}, Reason: {}", email, event.getReason());
        }

        // userId 기반 캐시 무효화 (Room 참가자 목록용)
        if (userId != null) {
            evictUserCacheByUserId(userId);
            log.info("User cache evicted by userId - UserId: {}, Reason: {}", userId, event.getReason());
        }
    }

    /**
     * SessionEndedEvent 처리 - 로그아웃/세션 만료 시 캐시 무효화
     */
    @Async
    @EventListener
    public void handleSessionEnded(SessionEndedEvent event) {
        log.debug("SessionEndedEvent received - UserId: {}, Reason: {}",
                event.getUserId(), event.getReason());

        String userId = event.getUserId();
        String email = userRepository.findById(userId)
                .map(User::getEmail)
                .orElse(null);

        // email 기반 캐시 무효화
        if (email != null) {
            evictUserCacheByEmail(email);
            log.info("User cache evicted by email on session end - Email: {}, Reason: {}",
                    email, event.getReason());
        }

        // userId 기반 캐시 무효화
        if (userId != null) {
            evictUserCacheByUserId(userId);
            log.info("User cache evicted by userId on session end - UserId: {}, Reason: {}",
                    userId, event.getReason());
        }
    }

    /**
     * 실제 캐시 무효화 수행 (email 기반)
     */
    @CacheEvict(value = CacheConfig.USER_PROFILE_CACHE, key = "#email")
    private void evictUserCacheByEmail(String email) {
        log.debug("Cache evicted for email: {}", email);
    }

    /**
     * 실제 캐시 무효화 수행 (userId 기반)
     */
    @CacheEvict(value = CacheConfig.USER_PROFILE_CACHE, key = "'userId:' + #userId")
    private void evictUserCacheByUserId(String userId) {
        log.debug("Cache evicted for userId: {}", userId);
    }
}
