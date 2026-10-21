package com.emocio.analysefoot.analyse;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionMatcherTest {

    private final QuestionMatcher matcher = new QuestionMatcher();

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
            "meilleur buteur | TOP_SCORER",
            "Qui a marqué le plus ? | TOP_SCORER",
            "le joueur avec le plus de buts | TOP_SCORER",
            "meilleur passeur | TOP_ASSIST",
            "qui a fait le plus de passes décisives ? | TOP_ASSIST",
            "le roi de la passe décisive | TOP_ASSIST",
            "joueur le plus agressif | MOST_YELLOW_CARDS",
            "qui a pris le plus de jaunes | MOST_YELLOW_CARDS",
            "le plus de cartons jaunes | MOST_YELLOW_CARDS",
            "joueur le moins fair-play | LEAST_FAIR_PLAY_PLAYER",
            "le joueur le plus sanctionné | LEAST_FAIR_PLAY_PLAYER",
            "quel joueur est le plus indiscipliné ? | LEAST_FAIR_PLAY_PLAYER",
            "meilleure équipe | BEST_TEAM",
            "qui est champion ? | BEST_TEAM",
            "qui a gagné la coupe du monde | BEST_TEAM",
            "équipe qui a marqué le plus de buts | TOP_SCORING_TEAM",
            "la meilleure attaque | TOP_SCORING_TEAM",
            "quelle équipe a le plus marqué ? | TOP_SCORING_TEAM",
            "équipe la moins fair-play | LEAST_FAIR_PLAY_TEAM",
            "l'équipe la plus sale | LEAST_FAIR_PLAY_TEAM",
            "EQUIPE la moins fair play | LEAST_FAIR_PLAY_TEAM",
            "joueur le plus fair-play | MOST_FAIR_PLAY_PLAYER",
            "le joueur le plus propre | MOST_FAIR_PLAY_PLAYER",
            "qui n'a jamais pris de carton ? | MOST_FAIR_PLAY_PLAYER",
    })
    void recognizesReformulations(String question, AnalyseCriterion expected) {
        QuestionMatcher.Match match = matcher.match(question);
        assertThat(match.candidates()).containsExactly(expected);
        assertThat(match.recognized()).isTrue();
    }

    @Test
    void leastFairPlayWithoutScopeIsAmbiguous() {
        QuestionMatcher.Match match = matcher.match("le moins fair-play");
        assertThat(match.ambiguous()).isTrue();
        assertThat(match.candidates()).containsExactlyInAnyOrder(
                AnalyseCriterion.LEAST_FAIR_PLAY_PLAYER, AnalyseCriterion.LEAST_FAIR_PLAY_TEAM);
    }

    @ParameterizedTest
    @ValueSource(strings = {"quel temps fera-t-il demain ?", "bonjour", "   "})
    void unrelatedQuestionIsNotRecognized(String question) {
        QuestionMatcher.Match match = matcher.match(question);
        assertThat(match.candidates()).isEmpty();
        assertThat(match.recognized()).isFalse();
    }

    @Test
    void normalizationIgnoresAccentsCaseAndPunctuation() {
        assertThat(QuestionMatcher.normalize("  L'ÉQUIPE, la moins Fair-Play !")).isEqualTo("l equipe la moins fair play");
    }
}
