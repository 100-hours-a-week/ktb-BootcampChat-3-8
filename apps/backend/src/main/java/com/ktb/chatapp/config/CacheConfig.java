package com.ktb.chatapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Cache 설정 (Redis 기반)
 * Cache-Aside 패턴을 사용한 사용자 정보 캐싱
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 캐시 이름 상수
     */
    public static final String USER_CACHE = "user";
    public static final String USER_PROFILE_CACHE = "userProfile";
    public static final String ROOM_PARTICIPANTS_CACHE = "roomParticipants";

    @Value("${cache.user.ttl:24h}")
    private String userCacheTtl;

    @Value("${cache.user-profile.ttl:24h}")
    private String userProfileCacheTtl;

    @Value("${cache.room-participants.ttl:30m}")
    private String roomParticipantsTtl;

    @Value("${cache.default.ttl:30m}")
    private String defaultCacheTtl;

    /**
     * Redis 기반 CacheManager 설정
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 기본 캐시 설정
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(parseDuration(defaultCacheTtl)) // 환경 변수로 설정
                .disableCachingNullValues() // null 값은 캐싱하지 않음
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()
                        )
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                        )
                );

        // 캐시별 개별 설정
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // user 캐시: UserDetailsService용
        cacheConfigurations.put(USER_CACHE,
                defaultConfig.entryTtl(parseDuration(userCacheTtl))
        );

        // userProfile 캐시: 사용자 프로필 정보용
        cacheConfigurations.put(USER_PROFILE_CACHE,
                defaultConfig.entryTtl(parseDuration(userProfileCacheTtl))
        );

        // roomParticipants 캐시: 방별 참가자 정보 (UserResponse 리스트)
        cacheConfigurations.put(ROOM_PARTICIPANTS_CACHE,
                defaultConfig.entryTtl(parseDuration(roomParticipantsTtl))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    /**
     * Duration 문자열 파싱 (예: "24h", "30m", "1d")
     */
    private Duration parseDuration(String durationStr) {
        return org.springframework.boot.convert.DurationStyle.detectAndParse(durationStr);
    }
}
