package com.ktb.chatapp.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 구성
 * - Standalone 모드 (로컬 개발)
 * - Master-Replica 모드 (프로덕션)
 */
@Configuration
@Slf4j
public class RedisConfig {

    @Value("${spring.data.redis.mode:standalone}")
    private String redisMode;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:#{null}}")
    private String redisPassword;

    @Value("${spring.data.redis.cluster.nodes:}")
    private String clusterNodes;

    @Value("${spring.data.redis.timeout:3000}")
    private int timeout;

    /**
     * Standalone Redis 구성 (로컬 개발용)
     */
    @Bean
    @ConditionalOnProperty(name = "spring.data.redis.mode", havingValue = "standalone", matchIfMissing = true)
    public RedisConnectionFactory standaloneRedisConnectionFactory() {
        log.info("Configuring Redis in STANDALONE mode: {}:{}", redisHost, redisPort);

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.setPassword(redisPassword);
        }

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(timeout))
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder()
                                .connectTimeout(Duration.ofMillis(timeout))
                                .build())
                        .timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(timeout)))
                        .build())
                .build();

        return new LettuceConnectionFactory(config, clientConfig);
    }

    /**
     * Master-Replica Redis 구성 (프로덕션용)
     * 3-node master-replica topology
     *
     * 특징:
     * - Read from Replica: 읽기 요청을 replica에서 처리
     * - Write to Master: 쓰기 요청은 master에서 처리
     * - Topology Refresh: 클러스터 상태 자동 갱신
     * - Failover: Master 장애 시 자동 replica 승격
     */
    @Bean
    @ConditionalOnProperty(name = "spring.data.redis.mode", havingValue = "cluster")
    public RedisConnectionFactory clusterRedisConnectionFactory() {
        log.info("Configuring Redis in CLUSTER mode with nodes: {}", clusterNodes);

        // Redis Cluster 노드 파싱
        List<String> nodes = Arrays.asList(clusterNodes.split(","));
        RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration(nodes);

        if (redisPassword != null && !redisPassword.isEmpty()) {
            clusterConfig.setPassword(redisPassword);
        }

        // Cluster Topology Refresh 설정
        ClusterTopologyRefreshOptions topologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
                .enablePeriodicRefresh(Duration.ofMinutes(10)) // 10분마다 클러스터 토폴로지 갱신
                .enableAllAdaptiveRefreshTriggers() // 모든 adaptive refresh 트리거 활성화
                .build();

        // Cluster Client 옵션
        ClusterClientOptions clientOptions = ClusterClientOptions.builder()
                .topologyRefreshOptions(topologyRefreshOptions)
                .socketOptions(SocketOptions.builder()
                        .connectTimeout(Duration.ofMillis(timeout))
                        .build())
                .timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(timeout)))
                .validateClusterNodeMembership(false) // 클러스터 노드 멤버십 검증 비활성화 (유연성 향상)
                .build();

        // Lettuce Client 구성
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(timeout))
                .readFrom(ReadFrom.REPLICA_PREFERRED) // Replica에서 우선적으로 읽기 (Master fallback)
                .clientOptions(clientOptions)
                .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(clusterConfig, clientConfig);

        log.info("Redis Cluster configured with {} nodes, ReadFrom: REPLICA_PREFERRED", nodes.size());
        return factory;
    }

    /**
     * RedisTemplate 구성
     * - Key: String Serializer
     * - Value: Jackson JSON Serializer
     * - Hash Key/Value: 동일한 Serializer 사용
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // JSON Serializer for values
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // Key Serializers
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value Serializers
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();

        log.info("RedisTemplate configured with Jackson JSON serialization");
        return template;
    }
}
