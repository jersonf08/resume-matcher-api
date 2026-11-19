package com.resumematcher.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.resumematcher.llm.LlmException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ExplanationSentencesTest {

    @Test
    void dosFrasesNoCambian() {
        String text = "Cumple los requisitos obligatorios. Le falta experiencia con AWS.";

        assertThat(ExplanationSentences.enforce(text)).isEqualTo(text);
    }

    @Test
    void tresFrasesNoCambian() {
        String text = "Cumple los requisitos obligatorios. ¿Tiene AWS? No lo menciona en su hoja de vida!";

        assertThat(ExplanationSentences.enforce(text)).isEqualTo(text);
    }

    @Test
    void cincoFrasesSeRecortanATres() {
        String text = "Primera frase. Segunda frase! Tercera frase? Cuarta frase. Quinta frase.";

        assertThat(ExplanationSentences.enforce(text))
                .isEqualTo("Primera frase. Segunda frase! Tercera frase?");
    }

    @Test
    void unaFraseLanzaExcepcion() {
        assertThatThrownBy(() -> ExplanationSentences.enforce("Cumple todos los requisitos."))
                .isInstanceOf(LlmException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void textoVacioLanzaExcepcion(String text) {
        assertThatThrownBy(() -> ExplanationSentences.enforce(text)).isInstanceOf(LlmException.class);
    }

    @Test
    void noCortaNumerosDecimales() {
        String text = "Tiene 3.5 años con Java y un ajuste del 87,5 %. Le falta Kubernetes.";

        assertThat(ExplanationSentences.split(text)).containsExactly(
                "Tiene 3.5 años con Java y un ajuste del 87,5 %.",
                "Le falta Kubernetes.");
        assertThat(ExplanationSentences.enforce(text)).isEqualTo(text);
    }
}
