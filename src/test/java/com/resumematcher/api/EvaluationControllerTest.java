package com.resumematcher.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
import com.resumematcher.service.EvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EvaluationController.class)
@Import(EvaluationService.class)
class EvaluationControllerTest {

    private static final String URL = "/api/evaluations";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LlmClient llmClient;

    @Test
    void devuelveScoreYExplicacionCuandoElLlmRespondeBien() throws Exception {
        when(llmClient.complete(anyString(), anyString())).thenReturn("""
                {"score": 85, "explanation": "El candidato cumple los requisitos principales. Le falta experiencia en Kubernetes."}""");

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobDescription": "Desarrollador Java con Spring Boot", "resume": "5 años con Java y Spring Boot"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(85))
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
