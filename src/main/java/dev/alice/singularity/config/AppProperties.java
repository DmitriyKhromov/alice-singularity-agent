package dev.alice.singularity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String timezone,
        GigaChat gigachat,
        Singularity singularity,
        Alice alice
) {
    public record GigaChat(String authKey, String scope, String model) {
    }

    public record Singularity(String apiToken, String baseUrl) {
    }

    public record Alice(String allowedUserIds, String greeting) {
    }
}
