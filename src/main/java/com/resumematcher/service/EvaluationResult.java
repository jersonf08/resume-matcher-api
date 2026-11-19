package com.resumematcher.service;

import com.resumematcher.scoring.VerifiedRequirement;
import java.util.List;

/** Resultado completo de una evaluación; el controlador decide qué parte exponer. */
public record EvaluationResult(int score, String explanation, List<VerifiedRequirement> requirements) {
}
