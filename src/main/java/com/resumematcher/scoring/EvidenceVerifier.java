package com.resumematcher.scoring;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Comprueba que cada cita del LLM exista de verdad en la hoja de vida. Es la defensa
 * contra alucinaciones: un requisito solo cuenta como cumplido si su evidencia es real.
 */
@Component
public class EvidenceVerifier {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern SURROUNDING_QUOTES =
            Pattern.compile("^[\"'“”‘’«»]+|[\"'“”‘’«»]+$");

    public List<VerifiedRequirement> verify(List<ExtractedRequirement> requirements, String resume) {
        String normalizedResume = normalize(resume);
        return requirements.stream()
                .map(requirement -> verify(requirement, normalizedResume))
                .toList();
    }

    private VerifiedRequirement verify(ExtractedRequirement requirement, String normalizedResume) {
        String evidence = requirement.evidence();
        String quote = evidence == null ? "" : normalize(SURROUNDING_QUOTES.matcher(evidence.strip()).replaceAll(""));

        if (quote.isEmpty()) {
            // Sin cita no hay cumplimiento. Si el modelo decía que sí se cumplía, era una afirmación sin respaldo.
            return new VerifiedRequirement(
                    requirement.requirement(), requirement.type(), false, null, !requirement.met());
        }

        boolean found = normalizedResume.contains(quote);
        return new VerifiedRequirement(
                requirement.requirement(), requirement.type(), requirement.met() && found, evidence, found);
    }

    /**
     * Normaliza para comparar sin depender de espacios, saltos de línea ni mayúsculas.
     * También revierte el escape de etiquetas que se aplica al construir el prompt,
     * porque el modelo ve "&amp;lt;" donde la hoja de vida original tenía "&lt;".
     */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String unescaped = text.replace("&lt;", "<").replace("&gt;", ">");
        String composed = Normalizer.normalize(unescaped, Normalizer.Form.NFC);
        return WHITESPACE.matcher(composed).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
    }
}
