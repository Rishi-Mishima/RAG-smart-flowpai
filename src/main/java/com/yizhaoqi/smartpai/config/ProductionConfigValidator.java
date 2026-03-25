package com.yizhaoqi.smartpai.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class ProductionConfigValidator implements CommandLineRunner {

    private final Environment environment;

    public ProductionConfigValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        if (!activeProfiles.contains("prod")) {
            return;
        }

        requireNonBlank("spring.datasource.url");
        requireNonBlank("spring.datasource.username");
        requireNonBlank("spring.datasource.password");
        requireNonBlank("jwt.secret-key");
        requireNonBlank("deepseek.api.url");
        requireNonBlank("deepseek.api.key");
        requireNonBlank("embedding.api.url");
        requireNonBlank("embedding.api.key");
        requireNonBlank("minio.endpoint");
        requireNonBlank("minio.accessKey");
        requireNonBlank("minio.secretKey");
        requireNonBlank("elasticsearch.host");
        requireNonBlank("elasticsearch.password");
        requireNonBlank("security.allowed-origins");

        String insecureTls = environment.getProperty("elasticsearch.insecure-trust-all-certificates", "false");
        if (Boolean.parseBoolean(insecureTls)) {
            throw new IllegalStateException("Production profile forbids elasticsearch.insecure-trust-all-certificates=true");
        }
    }

    private void requireNonBlank(String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required production config: " + key);
        }
    }
}
