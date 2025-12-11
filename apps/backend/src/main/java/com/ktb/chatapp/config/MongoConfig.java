package com.ktb.chatapp.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.util.StringUtils;

@Configuration
@EnableMongoAuditing
@Slf4j
public class MongoConfig {

    private static final String DEFAULT_STANDALONE_OPTIONS = "retryWrites=true&w=majority";

    @Value("${app.mongodb.mode:standalone}")
    private String mongoMode;

    @Value("${app.mongodb.uri:}")
    private String explicitUri;

    @Value("${spring.data.mongodb.uri:}")
    private String springMongoUri;

    @Value("${app.mongodb.host:localhost}")
    private String host;

    @Value("${app.mongodb.port:27017}")
    private int port;

    @Value("${app.mongodb.database:bootcamp-chat}")
    private String database;

    @Value("${app.mongodb.username:}")
    private String username;

    @Value("${app.mongodb.password:}")
    private String password;

    @Value("${app.mongodb.auth-database:admin}")
    private String authDatabase;

    @Value("${app.mongodb.replica-set.name:rs0}")
    private String replicaSetName;

    @Value("${app.mongodb.replica-set.hosts:}")
    private String replicaHosts;

    @Value("${app.mongodb.replica-set.options:readPreference=primaryPreferred&w=majority&retryWrites=true}")
    private String replicaOptions;

    @Bean
    public MongoClient mongoClient() {
        if (StringUtils.hasText(springMongoUri)) {
            log.info("Configuring MongoDB using spring.data.mongodb.uri property");
            return MongoClients.create(springMongoUri);
        }

        if (StringUtils.hasText(explicitUri)) {
            log.info("Configuring MongoDB using explicit connection string (mode hint: {})", mongoMode);
            return MongoClients.create(explicitUri);
        }

        if ("replica".equalsIgnoreCase(mongoMode)) {
            String uri = buildReplicaSetUri();
            log.info("MongoDB ReplicaSet mode configured: hosts={}, replicaSet={}",
                    replicaHosts, replicaSetName);
            return MongoClients.create(uri);
        }

        String uri = buildStandaloneUri();
        log.info("MongoDB standalone mode configured: {}:{}", host, port);
        return MongoClients.create(uri);
    }

    private String buildStandaloneUri() {
        String hosts = host + ":" + port;
        return buildUri(hosts, DEFAULT_STANDALONE_OPTIONS);
    }

    private String buildReplicaSetUri() {
        if (!StringUtils.hasText(replicaHosts)) {
            throw new IllegalStateException("ReplicaSet mode requires MONGO_REPLICA_HOSTS to be set");
        }

        StringBuilder options = new StringBuilder("replicaSet=" + replicaSetName);
        if (StringUtils.hasText(replicaOptions)) {
            options.append("&").append(trimQuery(replicaOptions));
        }
        return buildUri(replicaHosts, options.toString());
    }

    private String buildUri(String hosts, String options) {
        StringBuilder uri = new StringBuilder("mongodb://");
        String credentials = buildCredentials();
        if (StringUtils.hasText(credentials)) {
            uri.append(credentials).append("@");
        }

        uri.append(hosts.trim());
        uri.append("/").append(database);

        String query = buildQuery(options);
        if (StringUtils.hasText(query)) {
            uri.append("?").append(query);
        }

        return uri.toString();
    }

    private String buildCredentials() {
        if (!StringUtils.hasText(username)) {
            return "";
        }

        String encodedUser = urlEncode(username);
        String encodedPassword = StringUtils.hasText(password) ? urlEncode(password) : "";

        if (StringUtils.hasText(encodedPassword)) {
            return encodedUser + ":" + encodedPassword;
        }

        return encodedUser;
    }

    private String buildQuery(String options) {
        List<String> params = new ArrayList<>();

        if (StringUtils.hasText(options)) {
            params.add(trimQuery(options));
        }

        if (StringUtils.hasText(username)) {
            params.add("authSource=" + authDatabase);
        }

        return String.join("&", params);
    }

    private static String trimQuery(String query) {
        if (query == null) {
            return "";
        }

        String trimmed = query.trim();
        while (trimmed.startsWith("?") || trimmed.startsWith("&")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed;
    }

    private static String urlEncode(String input) {
        return URLEncoder.encode(input, StandardCharsets.UTF_8);
    }
}
