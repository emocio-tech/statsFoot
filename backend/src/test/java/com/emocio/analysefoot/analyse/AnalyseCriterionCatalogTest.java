package com.emocio.analysefoot.analyse;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cohérence du catalogue de l'onglet Analyse (cadrage, hooks) : chaque critère a ses fragments SQL, son libellé,
 * son explication et ses mots-clés, et son propre libellé est reconnu comme lui-même. Les requêtes elles-mêmes sont
 * exécutées sur PostgreSQL par WorldCupImportAndRatingTest.Analyse#everyCriterionRunsOnPostgres.
 */
class AnalyseCriterionCatalogTest {

    private final QuestionMatcher matcher = new QuestionMatcher();

    @ParameterizedTest
    @EnumSource(AnalyseCriterion.class)
    void criterionIsComplete(AnalyseCriterion criterion) {
        assertThat(criterion.label()).isNotBlank();
        assertThat(criterion.explanation()).isNotBlank().endsWith(".");
        assertThat(criterion.unit()).isNotBlank();
        assertThat(criterion.filter).isNotBlank();
        assertThat(criterion.value).isNotBlank();
        assertThat(criterion.order).isNotBlank();
        assertThat(criterion.keywords()).hasSizeGreaterThanOrEqualTo(3)
                .allSatisfy(k -> assertThat(k).isEqualTo(QuestionMatcher.normalize(k)));
        assertThat(criterion.toQuestion().id()).isEqualTo(criterion.name());
    }

    @ParameterizedTest
    @EnumSource(AnalyseCriterion.class)
    void ownLabelIsRecognizedAsItself(AnalyseCriterion criterion) {
        assertThat(matcher.match(criterion.label()).candidates()).containsExactly(criterion);
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
