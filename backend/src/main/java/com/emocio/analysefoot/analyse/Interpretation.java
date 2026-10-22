package com.emocio.analysefoot.analyse;

import java.util.List;

/**
 * Résultat de l'interprétation d'une question libre : une question du catalogue reconnue, une ambiguïté entre
 * plusieurs, ou aucune ; avec l'origine (mots-clés ou assistant).
 */
public record Interpretation(Outcome outcome, AnalyseCriterion criterion, List<AnalyseCriterion> alternatives,
                             Source source, boolean unrelated) {

    public Interpretation(Outcome outcome, AnalyseCriterion criterion, List<AnalyseCriterion> alternatives, Source source) {
        this(outcome, criterion, alternatives, source, false);
    }

    /** Aucune interprétation, et l'assistant a jugé la question sans rapport avec le catalogue : réponse libre possible. */
    public Interpretation asUnrelated() {
        return new Interpretation(outcome, criterion, alternatives, source, true);
    }

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
