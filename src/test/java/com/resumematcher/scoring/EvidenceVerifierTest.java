package com.resumematcher.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceVerifierTest {

    private static final String RESUME = """
            Ingeniero de Sistemas.
            5 años desarrollando APIs REST con Java 17 y   Spring Boot.
            Experiencia con MySQL y PostgreSQL. Uso de genéricos List<String>.""";

    private final EvidenceVerifier verifier = new EvidenceVerifier();

    @Test
    void aceptaCitaExacta() {
        VerifiedRequirement result = verifyOne(true, "Experiencia con MySQL y PostgreSQL.");

        assertThat(result.met()).isTrue();
        assertThat(result.verified()).isTrue();
        assertThat(result.evidence()).isEqualTo("Experiencia con MySQL y PostgreSQL.");
    }

    @Test
    void aceptaCitaConDiferenciasDeEspaciosYMayusculas() {
        // En la hoja de vida hay varios espacios antes de "Spring" y otras mayúsculas.
        VerifiedRequirement result = verifyOne(true, "  5 AÑOS desarrollando apis rest con java 17 y spring boot ");

        assertThat(result.met()).isTrue();
        assertThat(result.verified()).isTrue();
    }

    @Test
    void aceptaCitaQueCruzaUnSaltoDeLinea() {
        VerifiedRequirement result = verifyOne(true, "Ingeniero de Sistemas. 5 años");

        assertThat(result.met()).isTrue();
        assertThat(result.verified()).isTrue();
    }

    @Test
    void aceptaCitaEntreComillas() {
        VerifiedRequirement result = verifyOne(true, "\"Experiencia con MySQL\"");

        assertThat(result.met()).isTrue();
        assertThat(result.verified()).isTrue();
    }

    @Test
    void aceptaCitaDelTextoEscapadoQueVioElModelo() {
        VerifiedRequirement result = verifyOne(true, "genéricos List&lt;String&gt;");

        assertThat(result.met()).isTrue();
        assertThat(result.verified()).isTrue();
    }

    @Test
    void rechazaCitaInventada() {
        VerifiedRequirement result = verifyOne(true, "3 años administrando clústeres de Kubernetes");

        assertThat(result.met()).isFalse();
        assertThat(result.verified()).isFalse();
        // La cita se conserva para que el detalle muestre qué afirmó el modelo.
        assertThat(result.evidence()).isEqualTo("3 años administrando clústeres de Kubernetes");
    }

    @Test
    void rechazaCitaRealAmpliadaConTextoInventado() {
        VerifiedRequirement result = verifyOne(true, "Experiencia con MySQL, PostgreSQL y Oracle.");

        assertThat(result.met()).isFalse();
        assertThat(result.verified()).isFalse();
    }

    @Test
    void cumplidoSinCitaNoCuentaYNoSeVerifica() {
        VerifiedRequirement result = verifyOne(true, null);

        assertThat(result.met()).isFalse();
        assertThat(result.verified()).isNull();
    }

    @Test
    void noCumplidoSinCitaNoSeVerifica() {
        VerifiedRequirement result = verifyOne(false, null);

        assertThat(result.met()).isFalse();
        assertThat(result.verified()).isNull();
        assertThat(result.evidence()).isNull();
    }

    @Test
    void citaEnBlancoEquivaleASinCita() {
        VerifiedRequirement result = verifyOne(true, "   ");

        assertThat(result.met()).isFalse();
        assertThat(result.verified()).isNull();
        assertThat(result.evidence()).isNull();
    }

    @Test
    void noConvierteEnCumplidoLoQueElModeloMarcoComoNoCumplido() {
        VerifiedRequirement result = verifyOne(false, "Experiencia con MySQL");

        assertThat(result.met()).isFalse();
        assertThat(result.verified()).isTrue();
    }

    @Test
    void conservaRequisitoYTipo() {
        List<VerifiedRequirement> results = verifier.verify(List.of(
                new ExtractedRequirement("Java", RequirementType.MUST, true, "Java 17"),
                new ExtractedRequirement("Docker", RequirementType.NICE, false, null)), RESUME);

        assertThat(results).extracting(VerifiedRequirement::requirement, VerifiedRequirement::type)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Java", RequirementType.MUST),
                        org.assertj.core.groups.Tuple.tuple("Docker", RequirementType.NICE));
    }

    private VerifiedRequirement verifyOne(boolean met, String evidence) {
        return verifier.verify(
                List.of(new ExtractedRequirement("Requisito", RequirementType.MUST, met, evidence)),
                RESUME).get(0);
    }
}
