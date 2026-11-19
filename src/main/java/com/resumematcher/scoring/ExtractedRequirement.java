package com.resumematcher.scoring;

/**
 * Requisito tal como lo reporta el LLM, antes de verificar su evidencia.
 *
 * @param evidence cita textual de la hoja de vida, o {@code null} si el modelo no encontró evidencia
 */
public record ExtractedRequirement(String requirement, RequirementType type, boolean met, String evidence) {
}
