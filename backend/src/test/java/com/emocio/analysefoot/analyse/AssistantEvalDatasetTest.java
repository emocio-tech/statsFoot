package com.emocio.analysefoot.analyse;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Jeu de l'évaluation réelle, vérifié hors ligne : 5 formulations par question, aucune reconnue par mots-clés. */
class AssistantEvalDatasetTest {

    private final QuestionMatcher matcher = new QuestionMatcher();

    @Test
    void formulationsAreUnknownToKeywords() {
        assertThat(AssistantEvalTest.FORMULATIONS).hasSize(AnalyseCriterion.values().length)
                .allSatisfy((criterion, questions) -> assertThat(questions).hasSize(5)
                        .allSatisfy(q -> assertThat(matcher.match(q).recognized()).as(q).isFalse()));
        assertThat(AssistantEvalTest.TRAPS).hasSize(10)
                .allSatisfy(q -> assertThat(matcher.match(q).recognized()).as(q).isFalse());
    }
}
