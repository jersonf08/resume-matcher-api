package com.resumematcher.scoring;

/**
 * Requisito después de comprobar su cita contra la hoja de vida.
 *
 * @param met      cumplido solo si el modelo lo afirmó y la cita existe en la hoja de vida
 * @param verified {@code false} si el modelo afirmó algo que la hoja de vida no respalda
 *                 (cita inexistente o requisito cumplido sin cita)
 */
public record VerifiedRequirement(
        String requirement,
        RequirementType type,
        boolean met,
        String evidence,
        boolean verified) {
}
