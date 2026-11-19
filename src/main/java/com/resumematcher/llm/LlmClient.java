package com.resumematcher.llm;

/**
 * Abstracción mínima sobre un modelo de lenguaje. Permite cambiar de proveedor
 * o simular el modelo en las pruebas sin tocar la lógica de negocio.
 */
public interface LlmClient {

    /**
     * Envía un prompt al modelo y devuelve su respuesta en texto plano.
     *
     * @throws LlmException si la llamada falla o la respuesta no es utilizable
     */
    String complete(String systemPrompt, String userPrompt);
}
