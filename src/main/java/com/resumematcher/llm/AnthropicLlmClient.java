package com.resumematcher.llm;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Implementación de {@link LlmClient} sobre la Messages API de Anthropic,
 * usando {@link RestClient} directamente (sin SDK).
 */
@Component
public class AnthropicLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmClient.class);

    private final AnthropicProperties properties;
    private final RestClient restClient;

    public AnthropicLlmClient(AnthropicProperties properties, RestClient.Builder builder) {
        this.properties = properties;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("anthropic-version", properties.apiVersion())
                .build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (!properties.hasApiKey()) {
            throw new LlmException("ANTHROPIC_API_KEY no está configurada");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("max_tokens", properties.maxTokens());
        body.put("system", systemPrompt);
        body.put("messages", List.of(Map.of("role", "user", "content", userPrompt)));
        body.put("output_config", Map.of("effort", properties.effort()));
        if (properties.hasThinking()) {
            body.put("thinking", Map.of("type", properties.thinking()));
        }

        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            // Solo el código HTTP: el cuerpo del proveedor no se registra ni se propaga.
            log.warn("Anthropic respondió con estado HTTP {}", e.getStatusCode().value());
            throw new LlmException("Error HTTP del proveedor: " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            log.warn("No fue posible comunicarse con Anthropic: {}", e.getClass().getSimpleName());
            throw new LlmException("Error de comunicación con el proveedor", e);
        }

        return extractText(response);
    }

    private String extractText(JsonNode response) {
        if (response == null) {
            throw new LlmException("Respuesta vacía del proveedor");
        }

        String stopReason = response.path("stop_reason").asText("");
        if ("refusal".equals(stopReason)) {
            throw new LlmException("El modelo rechazó la solicitud");
        }
        if ("max_tokens".equals(stopReason)) {
            throw new LlmException("La respuesta del modelo quedó truncada");
        }

        StringBuilder text = new StringBuilder();
        for (JsonNode block : response.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText());
            }
        }
        if (text.isEmpty()) {
            throw new LlmException("La respuesta del modelo no contiene texto");
        }
        return text.toString();
    }
}
