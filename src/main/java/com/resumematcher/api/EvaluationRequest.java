package com.resumematcher.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EvaluationRequest(
        @NotBlank(message = "El campo 'jobDescription' es obligatorio y no puede estar vacío")
        @Size(max = 50_000, message = "El campo 'jobDescription' no puede superar 50000 caracteres")
        String jobDescription,

        @NotBlank(message = "El campo 'resume' es obligatorio y no puede estar vacío")
        @Size(max = 50_000, message = "El campo 'resume' no puede superar 50000 caracteres")
        String resume) {
}
