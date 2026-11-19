package com.resumematcher.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.resumematcher.scoring.VerifiedRequirement;
import java.util.List;

/**
 * Respuesta de la API. Por defecto solo {@code score} y {@code explanation};
 * {@code requirements} aparece únicamente con {@code ?detail=true}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvaluationResponse(int score, String explanation, List<VerifiedRequirement> requirements) {
}
