package com.emocio.analysefoot.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Rapport complet d'un joueur sur le tournoi. Les stats détaillées et les passes décisives sont FICTIVES
 * (noms listés dans {@code fictitiousFields}) ; une valeur inconnue vaut null.
 */
public record PlayerReport(
        Integer playerId,
        String name,
        Integer teamId,
        String team,
        boolean goalkeeper,
        Totals totals,
        List<MatchLine> matches,
        MatchLine bestMatch,
        List<RankLine> ranks,
        List<String> fictitiousFields) {

    public static final List<String> FICTITIOUS_FIELDS = List.of(
            "assists", "foulsCommitted", "freeKicksWon", "freeKicksConceded", "duelsWon", "duelsLost", "corners",
            "offsides", "distanceKm", "manOfTheMatchCount");

    /** Totaux du tournoi (vue v_player_tournament_stats). */
    public record Totals(
            Integer matchesPlayed,
            Integer matchesStarted,
            Integer minutesPlayed,
            Integer goals,
            Integer penaltyGoals,
            Integer ownGoals,
            Integer decisiveGoals,
            Integer assists,
            Integer yellowCards,
            Integer secondYellows,
            Integer directReds,
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
            Integer manOfTheMatchCount,
            BigDecimal rating,
            BigDecimal ratingWeighted,
            Integer captainMatches,
            Integer goalkeeperMatches) {
    }

    /** Un match du joueur ; {@code score} est donné du point de vue de son équipe. */
    public record MatchLine(
            Integer matchId,
            OffsetDateTime kickoffAt,
            String stage,
            String opponent,
            String score,
            Integer minutesPlayed,
            Integer goals,
            Integer assists,
            Integer yellowCards,
            Boolean sentOff,
            BigDecimal rating) {
    }

    /** Rang du joueur sur un critère ; {@code rank} null = non classé (hors de la population du critère). */
    public record RankLine(String questionId, String label, Integer rank, Integer outOf) {
    }
}
