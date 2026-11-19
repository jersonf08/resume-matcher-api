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
            // Sin cita no hay cumplimiento ni nada que verificar.
            return new VerifiedRequirement(requirement.requirement(), requirement.type(), false, null, null);
        }

        boolean found = containsWholeWords(normalizedResume, quote);
        return new VerifiedRequirement(
                requirement.requirement(), requirement.type(), requirement.met() && found, evidence, found);
    }

    /**
     * Busca la cita como palabras completas: "Java" no debe validarse contra "JavaScript".
     * Se usan lookarounds con letras y dígitos Unicode en lugar de {@code \b}, porque
     * {@code \b} de Java no trata las letras con tilde como parte de una palabra.
     */
    static boolean containsWholeWords(String normalizedText, String normalizedQuote) {
        Pattern pattern = Pattern.compile(
                "(?<![\\p{L}\\p{N}])" + Pattern.quote(normalizedQuote) + "(?![\\p{L}\\p{N}])");
        return pattern.matcher(normalizedText).find();
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
