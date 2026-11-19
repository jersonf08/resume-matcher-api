package com.resumematcher.service;

import com.resumematcher.llm.LlmException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Salvaguarda del enunciado: la explicación debe tener 2 o 3 frases. El prompt lo pide,
 * pero el código no confía en que el modelo lo cumpla.
 */
final class ExplanationSentences {

    static final int MIN_SENTENCES = 2;
    static final int MAX_SENTENCES = 3;

    /**
     * Fin de frase: '.', '!' o '?' seguido de espacio. Exigir el espacio evita cortar
     * números decimales como "3.5" (y "87,5" usa coma, así que no se ve afectado).
     */
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+");

    private ExplanationSentences() {
    }

    static List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(SENTENCE_BOUNDARY.split(text.strip()))
                .filter(sentence -> !sentence.isBlank())
                .toList();
    }

    /**
     * Devuelve la explicación con a lo sumo 3 frases.
     *
     * @throws LlmException si tiene menos de 2 frases
     */
    static String enforce(String text) {
        List<String> sentences = split(text);
        if (sentences.size() < MIN_SENTENCES) {
            throw new LlmException("La explicación del modelo tiene menos de " + MIN_SENTENCES + " frases");
        }
        if (sentences.size() <= MAX_SENTENCES) {
            return text.strip();
        }
        return String.join(" ", sentences.subList(0, MAX_SENTENCES));
    }
}
