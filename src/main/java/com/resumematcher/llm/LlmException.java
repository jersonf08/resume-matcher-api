package com.resumematcher.llm;

/**
 * Error al obtener una respuesta válida del LLM. Su mensaje es interno (para logs);
 * nunca se devuelve tal cual al cliente de la API.
 */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
