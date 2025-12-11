package com.ktb.chatapp.config;

import io.lettuce.core.ReadFrom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStaticMasterReplicaConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        // 1. Master와 Replica 정보 설정
        
        // Master Node (쓰기 담당)
        RedisStaticMasterReplicaConfiguration clientConfig = 
                new RedisStaticMasterReplicaConfiguration("10.0.101.247", 6379);

        // Replica Nodes (읽기 담당)
        clientConfig.addNode("10.0.101.30", 6380); 
        clientConfig.addNode("10.0.101.150", 6381);

        // 2. 읽기 분산 전략 설정 (핵심 기능)
        // REPLICA_PREFERRED: 읽기 요청은 가능하면 Replica로 보냄.
        LettuceClientConfiguration lettuceConfig = LettuceClientConfiguration.builder()
                .readFrom(ReadFrom.REPLICA_PREFERRED)
                .build();

        return new LettuceConnectionFactory(clientConfig, lettuceConfig);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory());
        
        // 데이터가 깨져 보이지 않도록 직렬화 설정 (Key, Value 모두 문자열 처리)
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        
        return template;
    }
}