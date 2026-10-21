package com.emocio.analysefoot.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Rapport complet d'une équipe sur le tournoi. Les stats détaillées sont FICTIVES (noms listés dans
 * {@code fictitiousFields}) ; une valeur inconnue vaut null.
 */
public record TeamReport(
        Integer teamId,
        String team,
        Totals totals,
        List<MatchLine> matches,
        List<SquadLine> squad,
        SquadLine topScorer,
        SquadLine bestPlayer,
        List<String> fictitiousFields) {

    public static final List<String> FICTITIOUS_FIELDS = List.of(
            "foulsCommitted", "freeKicksWon", "freeKicksConceded", "duelsWon", "duelsLost", "corners", "offsides",
            "distanceKm", "assists");

    /** Totaux du tournoi (vue v_team_tournament_stats). */
    public record Totals(
            Integer matchesPlayed,
            Integer wins,
            Integer draws,
            Integer losses,
            Integer points,
            Integer goalsFor,
            Integer goalsAgainst,
            Integer yellowCards,
            Integer redCards,
            Integer fairPlayPoints,
            Integer foulsCommitted,
            Integer freeKicksWon,
            Integer freeKicksConceded,
            Integer duelsWon,
            Integer duelsLost,
            Integer corners,
            Integer offsides,
            BigDecimal distanceKm,
            BigDecimal rating,
            String stageReached,
            Integer finalPosition) {
    }

    /** Un match du parcours ; scores et tirs au but du point de vue de l'équipe. */
    public record MatchLine(
            Integer matchId,
            OffsetDateTime kickoffAt,
            String stage,
            String opponent,
            String score,
            String penalties,
            String result,
            BigDecimal rating) {
    }

    /** Un joueur de l'effectif entré en jeu. */
    public record SquadLine(
            Integer playerId,
            String name,
            Boolean goalkeeper,
            Integer matchesPlayed,
            Integer minutesPlayed,
            Integer goals,
            Integer assists,
            Integer yellowCards,
            Integer redCards,
            BigDecimal ratingWeighted) {
    }
}
