package com.emocio.analysefoot.dto;

import java.util.List;

/**
 * Réponse à une question. {@code status} : ANSWERED, AMBIGUOUS, NOT_RECOGNIZED ou NO_DATA ;
 * {@code winners} = rang 1 (ex aequo compris), {@code top} = rang 5 au plus (peut dépasser 5 lignes) ;
 * {@code interpretation} dit comment une question libre a été comprise (null pour une suggestion cliquée).
 */
public record AnalyseAnswer(
        String status,
        AnalyseQuestion question,
        String explanation,
        boolean fictitious,
        List<RankedEntry> winners,
        List<RankedEntry> top,
        List<AnalyseQuestion> candidates,
        AnalyseInterpretation interpretation) {
}
