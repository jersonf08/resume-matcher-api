package com.resumematcher.scoring;

import com.resumematcher.llm.LlmException;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Calcula el puntaje de forma determinística a partir de los requisitos verificados.
 * El mismo conjunto de requisitos produce siempre el mismo número.
 */
@Component
public class ScoreCalculator {

    static final double MUST_WEIGHT = 75.0;
    static final double NICE_WEIGHT = 25.0;

    /**
     * Con requisitos de ambos tipos: 75 × (MUST cumplidos / MUST) + 25 × (NICE cumplidos / NICE).
     * Con un solo tipo: 100 × cumplidos / total.
     *
     * @throws LlmException si no hay requisitos (el modelo no extrajo nada evaluable)
     */
    public int calculate(List<VerifiedRequirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            throw new LlmException("El modelo no extrajo requisitos del cargo");
        }

        long mustTotal = count(requirements, RequirementType.MUST, false);
        long mustMet = count(requirements, RequirementType.MUST, true);
        long niceTotal = count(requirements, RequirementType.NICE, false);
        long niceMet = count(requirements, RequirementType.NICE, true);

        double score;
        if (mustTotal > 0 && niceTotal > 0) {
            score = MUST_WEIGHT * mustMet / mustTotal + NICE_WEIGHT * niceMet / niceTotal;
        } else if (mustTotal > 0) {
            score = 100.0 * mustMet / mustTotal;
        } else {
            score = 100.0 * niceMet / niceTotal;
        }

        return (int) Math.max(0, Math.min(100, Math.round(score)));
    }

    private static long count(List<VerifiedRequirement> requirements, RequirementType type, boolean onlyMet) {
        return requirements.stream()
                .filter(r -> r.type() == type)
                .filter(r -> !onlyMet || r.met())
                .count();
    }
}
