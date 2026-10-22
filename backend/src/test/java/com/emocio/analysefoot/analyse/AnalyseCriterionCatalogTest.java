package com.emocio.analysefoot.analyse;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cohérence des questions suggérées de l'onglet Analyse (cadrage, hooks) : chaque critère a ses fragments SQL, son
 * libellé et son explication. Les requêtes elles-mêmes sont exécutées sur PostgreSQL par
 * WorldCupImportAndRatingTest.Analyse#everyCriterionRunsOnPostgres.
 */
class AnalyseCriterionCatalogTest {

    @ParameterizedTest
    @EnumSource(AnalyseCriterion.class)
    void criterionIsComplete(AnalyseCriterion criterion) {
        assertThat(criterion.label()).isNotBlank();
        assertThat(criterion.explanation()).isNotBlank().endsWith(".");
        assertThat(criterion.unit()).isNotBlank();
        assertThat(criterion.filter).isNotBlank();
        assertThat(criterion.value).isNotBlank();
        assertThat(criterion.order).isNotBlank();
        assertThat(criterion.toQuestion().id()).isEqualTo(criterion.name());
    }

    @ParameterizedTest
    @EnumSource(AnalyseCriterion.class)
    void sqlFragmentsAreConstants(AnalyseCriterion criterion) {
        // les fragments viennent du code, jamais d'une saisie : ni paramètre ni séparateur d'instruction
        for (String fragment : new String[] {criterion.filter, criterion.value, criterion.order}) {
            assertThat(fragment).doesNotContain(";", "--", ":");
        }
    }
}
