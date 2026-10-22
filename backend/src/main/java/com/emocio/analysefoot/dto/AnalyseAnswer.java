package com.emocio.analysefoot.dto;

import java.util.List;

/**
 * Réponse à une question. {@code status} : ANSWERED, AMBIGUOUS, NOT_RECOGNIZED, NO_DATA,
 * FOUND (un joueur ou une équipe trouvé par son nom : {@code top} le contient) ou CHOOSE (plusieurs, à départager) ;
 * {@code winners} = rang 1 (ex aequo compris), {@code top} = rang 5 au plus (peut dépasser 5 lignes) ;
 * {@code interpretation} dit comment une question libre a été comprise (null pour une suggestion cliquée) ;
 * FREE_TEXT (question hors catalogue, réponse libre du LLM dans {@code text}) ;
 * {@code trace} liste les échanges de la requête (appli, LLM, tool, SQL), vide si la trace est désactivée.
 */
public record AnalyseAnswer(
        String status,
        AnalyseQuestion question,
        String explanation,
        boolean fictitious,
        List<RankedEntry> winners,
        List<RankedEntry> top,
        List<AnalyseQuestion> candidates,
        AnalyseInterpretation interpretation,
        List<TraceStep> trace,
        String text) {

    public AnalyseAnswer(String status, AnalyseQuestion question, String explanation, boolean fictitious,
                         List<RankedEntry> winners, List<RankedEntry> top, List<AnalyseQuestion> candidates,
                         AnalyseInterpretation interpretation) {
        this(status, question, explanation, fictitious, winners, top, candidates, interpretation, List.of(), null);
    }

    public AnalyseAnswer withTrace(List<TraceStep> trace) {
        return new AnalyseAnswer(status, question, explanation, fictitious, winners, top, candidates, interpretation, trace, text);
    }

    /** Réponse libre du LLM (statut FREE_TEXT) : un texte court, sans rapport détaillé. */
    public static AnalyseAnswer freeText(String text, AnalyseInterpretation interpretation) {
        return new AnalyseAnswer("FREE_TEXT", null, null, false, List.of(), List.of(), List.of(), interpretation,
                List.of(), text);
    }
}
