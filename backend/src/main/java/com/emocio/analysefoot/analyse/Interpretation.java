package com.emocio.analysefoot.analyse;

import java.util.List;

/**
 * Résultat de l'interprétation d'une question libre : une question du catalogue reconnue, une ambiguïté entre
 * plusieurs, ou aucune ; avec l'origine (mots-clés ou assistant).
 */
public record Interpretation(Outcome outcome, AnalyseCriterion criterion, List<AnalyseCriterion> alternatives,
                             Source source) {

    public enum Outcome { RECOGNIZED, AMBIGUOUS, NONE }

    public enum Source { KEYWORDS, ASSISTANT }

    public static Interpretation none(Source source) {
        return new Interpretation(Outcome.NONE, null, List.of(), source);
    }

    public static Interpretation recognized(AnalyseCriterion criterion, Source source) {
        return new Interpretation(Outcome.RECOGNIZED, criterion, List.of(), source);
    }

    public static Interpretation ambiguous(List<AnalyseCriterion> alternatives, Source source) {
        return new Interpretation(Outcome.AMBIGUOUS, null, List.copyOf(alternatives), source);
    }
}
