package com.resumematcher.scoring;

/**
 * Requisito después de comprobar su cita contra la hoja de vida.
 *
 * @param met      cumplido solo si el modelo lo afirmó y la cita existe en la hoja de vida
 * @param verified {@code true} si la cita existe en la hoja de vida, {@code false} si el modelo
 *                 la inventó, y {@code null} si no hay cita que verificar
 */
public record VerifiedRequirement(
        String requirement,
        RequirementType type,
        boolean met,
        String evidence,
        Boolean verified) {
}
