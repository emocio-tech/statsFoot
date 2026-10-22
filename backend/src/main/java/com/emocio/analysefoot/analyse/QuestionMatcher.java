package com.emocio.analysefoot.analyse;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reconnaît une question libre parmi les questions du catalogue, uniquement par mots-clés (aucun service externe).
 * Chaque mot-clé présent vaut {@value #KEYWORD_POINTS} points ; un mot de portée (« équipe », « joueur », « qui »...)
 * avantage les questions de cette portée, ce qui départage les mots-clés communs (« plus de buts »).
 * Score maximal unique : question reconnue ; plusieurs ex aequo : ambiguïté ; aucun mot-clé : non reconnue.
 */
@Component
public class QuestionMatcher {

    static final int KEYWORD_POINTS = 3;

    /** Mots qui orientent vers une portée, avec leur poids. */
    private static final Map<String, AnalyseCriterion.Scope> SCOPE_WORDS = Map.of(
            "joueur", AnalyseCriterion.Scope.PLAYER,
            "joueurs", AnalyseCriterion.Scope.PLAYER,
            "equipe", AnalyseCriterion.Scope.TEAM,
            "equipes", AnalyseCriterion.Scope.TEAM,
            "selection", AnalyseCriterion.Scope.TEAM,
            "pays", AnalyseCriterion.Scope.TEAM,
            "nation", AnalyseCriterion.Scope.TEAM);

    private static final int SCOPE_POINTS = 2;

    /** « qui » désigne plutôt une personne, mais moins nettement que « joueur ». */
    private static final int WHO_POINTS = 1;

    /** Résultat : questions retenues (une seule si reconnue, plusieurs si ambiguë, aucune sinon). */
    public record Match(List<AnalyseCriterion> candidates) {

        public boolean recognized() {
            return candidates.size() == 1;
        }

        public boolean ambiguous() {
            return candidates.size() > 1;
        }
    }

    public Match match(String question) {
        String text = " " + normalize(question) + " ";
        int[] scores = new int[AnalyseCriterion.values().length];
        for (AnalyseCriterion criterion : AnalyseCriterion.values()) {
            long keywords = criterion.keywords().stream().filter(k -> text.contains(" " + k + " ")).count();
            if (keywords > 0) {
                scores[criterion.ordinal()] = (int) keywords * KEYWORD_POINTS + scopeBonus(text, criterion.scope());
            }
        }
        int best = Arrays.stream(scores).max().orElse(0);
        if (best == 0) {
            return new Match(List.of());
        }
        return new Match(Arrays.stream(AnalyseCriterion.values())
                .filter(c -> scores[c.ordinal()] == best)
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .toList());
    }

    private static int scopeBonus(String text, AnalyseCriterion.Scope scope) {
        int bonus = SCOPE_WORDS.entrySet().stream()
                .filter(e -> e.getValue() == scope && text.contains(" " + e.getKey() + " "))
                .findAny().map(e -> SCOPE_POINTS).orElse(0);
        if (scope == AnalyseCriterion.Scope.PLAYER && text.contains(" qui ")) {
            bonus += WHO_POINTS;
        }
        return bonus;
    }

    /** Minuscules, sans accents, ponctuation remplacée par des espaces, espaces simples. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
