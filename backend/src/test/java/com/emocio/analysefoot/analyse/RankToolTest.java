package com.emocio.analysefoot.analyse;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Le tool rank commun à tous les fournisseurs : schéma borné au catalogue, consignes et validation du choix. */
class RankToolTest {

    @Test
    void schemaIsStrictAndBoundToTheCatalogue() {
        Map<String, Object> schema = RankTool.inputSchema();
        List<String> ids = Arrays.stream(AnalyseCriterion.values()).map(Enum::name).toList();

        assertThat(schema.get("additionalProperties")).isEqualTo(false);
        assertThat(schema.get("required")).isEqualTo(List.of("criterion", "scope", "alternatives"));
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> properties = (Map<String, Map<String, Object>>) schema.get("properties");
        List<String> withNone = new ArrayList<>(ids);
        withNone.add("NONE");
        assertThat(properties.get("criterion").get("enum")).isEqualTo(withNone);
        assertThat(properties.get("scope").get("enum")).isEqualTo(List.of("PLAYER", "TEAM", "UNKNOWN"));
        @SuppressWarnings("unchecked")
        Map<String, Object> items = (Map<String, Object>) properties.get("alternatives").get("items");
        assertThat(items.get("enum")).isEqualTo(ids);
    }

    @Test
    void systemPromptDescribesEveryCriterion() {
        assertThat(AnalyseCriterion.values()).allSatisfy(c ->
                assertThat(RankTool.systemPrompt()).contains(c.name(), c.label()));
    }

    @Test
    void validChoiceIsRecognized() {
        assertThat(parse("TOP_SCORING_TEAM", "TEAM", List.of())).isEqualTo(
                Interpretation.recognized(AnalyseCriterion.TOP_SCORING_TEAM, Interpretation.Source.ASSISTANT));
        // le proxy renvoie parfois l'identifiant retenu parmi les alternatives
        assertThat(parse("TOP_SCORING_TEAM", "TEAM", List.of("TOP_SCORING_TEAM")).criterion())
                .isEqualTo(AnalyseCriterion.TOP_SCORING_TEAM);
    }

    @Test
    void noneIsNoInterpretation() {
        assertThat(parse("NONE", "UNKNOWN", List.of()).outcome()).isEqualTo(Interpretation.Outcome.NONE);
    }

    @Test
    void alternativesAreAnAmbiguity() {
        Interpretation interpretation = parse("NONE", "UNKNOWN", List.of("LEAST_FAIR_PLAY_PLAYER", "LEAST_FAIR_PLAY_TEAM"));
        assertThat(interpretation.outcome()).isEqualTo(Interpretation.Outcome.AMBIGUOUS);
        assertThat(interpretation.alternatives()).containsExactly(
                AnalyseCriterion.LEAST_FAIR_PLAY_PLAYER, AnalyseCriterion.LEAST_FAIR_PLAY_TEAM);
    }

    @Test
    void invalidInputsAreRejected() {
        assertThat(parse("SALARY", "PLAYER", List.of()).outcome()).isEqualTo(Interpretation.Outcome.NONE);
        assertThat(parse("TOP_SCORER", "TEAM", List.of()).outcome()).isEqualTo(Interpretation.Outcome.NONE);
        assertThat(parse("NONE", "UNKNOWN", List.of("NONE", "TOP_SCORER")).outcome()).isEqualTo(Interpretation.Outcome.NONE);
        assertThat(parse("NONE", "UNKNOWN", List.of("TOP_SCORER", "TOP_SCORER")).outcome()).isEqualTo(Interpretation.Outcome.NONE);
        assertThat(RankTool.parse(null).outcome()).isEqualTo(Interpretation.Outcome.NONE);
        assertThat(RankTool.parse(new HashMap<>(Map.of("criterion", "TOP_SCORER"))).outcome()).isEqualTo(Interpretation.Outcome.NONE);
        assertThat(RankTool.parse(new HashMap<>(Map.of("criterion", 3, "scope", "PLAYER", "alternatives", List.of()))).outcome())
                .isEqualTo(Interpretation.Outcome.NONE);
    }

    private static Interpretation parse(String criterion, String scope, List<String> alternatives) {
        return RankTool.parse(new HashMap<>(Map.of("criterion", criterion, "scope", scope, "alternatives", alternatives)));
    }
}
