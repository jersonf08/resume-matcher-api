package com.resumematcher.scoring;

import static com.resumematcher.scoring.RequirementType.MUST;
import static com.resumematcher.scoring.RequirementType.NICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.resumematcher.llm.LlmException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScoreCalculatorTest {

    private final ScoreCalculator calculator = new ScoreCalculator();

    @Test
    void soloObligatorios() {
        // 100 × 2/3 = 66,67
        assertThat(calculator.calculate(requirements(MUST, 3, 2))).isEqualTo(67);
    }

    @Test
    void soloDeseables() {
        // 100 × 1/4 = 25
        assertThat(calculator.calculate(requirements(NICE, 4, 1))).isEqualTo(25);
    }

    @Test
    void mixtosPonderan75Y25() {
        // 75 × 3/4 + 25 × 1/2 = 56,25 + 12,5 = 68,75
        List<VerifiedRequirement> mixed = new ArrayList<>(requirements(MUST, 4, 3));
        mixed.addAll(requirements(NICE, 2, 1));

        assertThat(calculator.calculate(mixed)).isEqualTo(69);
    }

    @Test
    void todosLosObligatoriosYNingunDeseableDan75() {
        List<VerifiedRequirement> mixed = new ArrayList<>(requirements(MUST, 2, 2));
        mixed.addAll(requirements(NICE, 3, 0));

        assertThat(calculator.calculate(mixed)).isEqualTo(75);
    }

    @Test
    void todoCumplidoDa100() {
        List<VerifiedRequirement> all = new ArrayList<>(requirements(MUST, 3, 3));
        all.addAll(requirements(NICE, 2, 2));

        assertThat(calculator.calculate(all)).isEqualTo(100);
    }

    @Test
    void nadaCumplidoDa0() {
        List<VerifiedRequirement> none = new ArrayList<>(requirements(MUST, 3, 0));
        none.addAll(requirements(NICE, 2, 0));

        assertThat(calculator.calculate(none)).isZero();
    }

    @Test
    void listaVaciaLanzaExcepcion() {
        assertThatThrownBy(() -> calculator.calculate(List.of())).isInstanceOf(LlmException.class);
    }

    @Test
    void listaNulaLanzaExcepcion() {
        assertThatThrownBy(() -> calculator.calculate(null)).isInstanceOf(LlmException.class);
    }

    /** Crea {@code total} requisitos del tipo indicado, de los cuales {@code met} están cumplidos. */
    private static List<VerifiedRequirement> requirements(RequirementType type, int total, int met) {
        List<VerifiedRequirement> list = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            boolean isMet = i < met;
            list.add(new VerifiedRequirement(
                    type + " " + i, type, isMet, isMet ? "cita" : null, isMet ? Boolean.TRUE : null));
        }
        return list;
    }
}
