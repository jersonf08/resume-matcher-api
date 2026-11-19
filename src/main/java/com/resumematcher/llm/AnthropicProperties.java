package com.resumematcher.llm;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "anthropic")
public record AnthropicProperties(
        String apiKey,
        String baseUrl,
        String apiVersion,
        String model,
        String effort,
        int maxTokens,
        Duration connectTimeout,
        Duration readTimeout) {

    /** Evita que la clave aparezca si alguien imprime este objeto en un log. */
    @Override
    public String toString() {
        return "AnthropicProperties[baseUrl=%s, model=%s, effort=%s, maxTokens=%d, apiKey=%s]"
                .formatted(baseUrl, model, effort, maxTokens, hasApiKey() ? "****" : "<vacía>");
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
