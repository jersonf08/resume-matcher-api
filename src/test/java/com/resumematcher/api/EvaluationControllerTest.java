package com.resumematcher.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resumematcher.llm.LlmClient;
import com.resumematcher.llm.LlmException;
import com.resumematcher.scoring.EvidenceVerifier;
import com.resumematcher.scoring.ScoreCalculator;
import com.resumematcher.service.EvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EvaluationController.class)
@Import({EvaluationService.class, EvidenceVerifier.class, ScoreCalculator.class})
class EvaluationControllerTest {

    private static final String URL = "/api/evaluations";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LlmClient llmClient;

    private static final String REQUEST_BODY = """
            {"jobDescription": "Desarrollador Java con Spring Boot. Deseable Kubernetes.",
             "resume": "5 años con Java y Spring Boot en proyectos bancarios."}""";

    private static final String LLM_RESPONSE = """
            {"requirements": [
              {"requirement": "Java", "type": "MUST", "met": true, "evidence": "5 años con Java"},
              {"requirement": "Spring Boot", "type": "MUST", "met": true, "evidence": "Spring Boot"},
              {"requirement": "Kubernetes", "type": "NICE", "met": false, "evidence": null}
            ], "explanation": "Cumple los requisitos obligatorios. No muestra experiencia con Kubernetes."}""";

    @Test
    void porDefectoDevuelveSoloScoreYExplanation() throws Exception {
        when(llmClient.complete(anyString(), anyString())).thenReturn(LLM_RESPONSE);

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(2)))
                .andExpect(jsonPath("$.score").value(75))
                .andExpect(jsonPath("$.explanation").exists())
                .andExpect(jsonPath("$.requirements").doesNotExist());
    }

    @Test
    void conDetailTrueIncluyeLosRequisitosVerificados() throws Exception {
        when(llmClient.complete(anyString(), anyString())).thenReturn(LLM_RESPONSE);

        mockMvc.perform(post(URL + "?detail=true").contentType(MediaType.APPLICATION_JSON).content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(75))
                .andExpect(jsonPath("$.requirements", hasSize(3)))
                .andExpect(jsonPath("$.requirements[0].requirement").value("Java"))
                .andExpect(jsonPath("$.requirements[0].type").value("MUST"))
                .andExpect(jsonPath("$.requirements[0].met").value(true))
                .andExpect(jsonPath("$.requirements[0].evidence").value("5 años con Java"))
                .andExpect(jsonPath("$.requirements[0].verified").value(true))
                .andExpect(jsonPath("$.requirements[2].type").value("NICE"))
                .andExpect(jsonPath("$.requirements[2].met").value(false))
                // evidence nula se muestra explícitamente dentro del detalle.
                .andExpect(jsonPath("$.requirements[2].evidence").value(nullValue()))
                .andExpect(jsonPath("$.requirements[2].verified").value(true));
    }

    @Test
    void unaCitaInventadaPorElLlmBajaElPuntaje() throws Exception {
        // El modelo afirma que ambos obligatorios se cumplen, pero la cita de Kubernetes no está en la hoja de vida.
        when(llmClient.complete(anyString(), anyString())).thenReturn("""
                {"requirements": [
                  {"requirement": "Java", "type": "MUST", "met": true, "evidence": "5 años con Java"},
                  {"requirement": "Kubernetes", "type": "MUST", "met": true, "evidence": "3 años administrando Kubernetes"}
                ], "explanation": "Cumple todos los requisitos."}""");

        mockMvc.perform(post(URL + "?detail=true").contentType(MediaType.APPLICATION_JSON).content(REQUEST_BODY))
                .andExpect(status().isOk())
                // Sin verificación serían 100; con la cita rechazada queda 1 de 2 obligatorios.
                .andExpect(jsonPath("$.score").value(50))
                .andExpect(jsonPath("$.requirements[1].met").value(false))
                .andExpect(jsonPath("$.requirements[1].verified").value(false))
                .andExpect(jsonPath("$.requirements[1].evidence").value("3 años administrando Kubernetes"));
    }

    @Test
    void devuelve400CuandoDetailNoEsBooleano() throws Exception {
        mockMvc.perform(post(URL + "?detail=abc").contentType(MediaType.APPLICATION_JSON).content(REQUEST_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("detail")));

        verifyNoInteractions(llmClient);
    }

    @Test
    void devuelve502CuandoElLlmNoExtraeRequisitos() throws Exception {
        when(llmClient.complete(anyString(), anyString())).thenReturn("""
                {"requirements": [], "explanation": "No encontré requisitos."}""");

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(REQUEST_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value(GlobalExceptionHandler.LLM_UNAVAILABLE_MESSAGE));
    }

    @Test
    void devuelveScoreYExplicacionCuandoElLlmRespondeBien() throws Exception {
        when(llmClient.complete(anyString(), anyString())).thenReturn("""
                {"requirements": [
                  {"requirement": "Java", "type": "MUST", "met": true, "evidence": "5 años con Java"},
                  {"requirement": "Spring Boot", "type": "MUST", "met": true, "evidence": "Spring Boot"},
                  {"requirement": "Kubernetes", "type": "NICE", "met": false, "evidence": null}
                ], "explanation": "El candidato cumple los requisitos principales. Le falta experiencia en Kubernetes."}""");

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobDescription": "Desarrollador Java con Spring Boot", "resume": "5 años con Java y Spring Boot"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(75))
                .andExpect(jsonPath("$.explanation").value(containsString("requisitos principales")));

        verify(llmClient).complete(anyString(), contains("<resume>\n5 años con Java y Spring Boot\n</resume>"));
    }

    @Test
    void devuelve400CuandoFaltaResume() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobDescription": "Desarrollador Java"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El campo 'resume' es obligatorio y no puede estar vacío"));

        verifyNoInteractions(llmClient);
    }

    @Test
    void devuelve400CuandoJobDescriptionEstaVacio() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobDescription": "   ", "resume": "5 años con Java"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El campo 'jobDescription' es obligatorio y no puede estar vacío"));

        verifyNoInteractions(llmClient);
    }

    @Test
    void devuelve400CuandoElCuerpoNoEsJsonValido() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("JSON válido")));

        verifyNoInteractions(llmClient);
    }

    @Test
    void devuelve502ConMensajeGenericoCuandoElLlmFalla() throws Exception {
        when(llmClient.complete(anyString(), anyString()))
                .thenThrow(new LlmException("Error HTTP del proveedor: 401 invalid x-api-key"));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobDescription": "Desarrollador Java", "resume": "5 años con Java"}"""))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value(GlobalExceptionHandler.LLM_UNAVAILABLE_MESSAGE))
                .andExpect(content().string(not(containsString("401"))))
                .andExpect(content().string(not(containsString("api-key"))));
    }

    @Test
    void devuelve502CuandoElLlmRespondeAlgoInvalido() throws Exception {
        when(llmClient.complete(anyString(), anyString())).thenReturn("Lo siento, no puedo ayudar con eso.");

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobDescription": "Desarrollador Java", "resume": "5 años con Java"}"""))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value(GlobalExceptionHandler.LLM_UNAVAILABLE_MESSAGE));
    }
}
