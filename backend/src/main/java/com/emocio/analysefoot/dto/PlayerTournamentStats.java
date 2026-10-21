package com.emocio.analysefoot.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Totaux d'un joueur sur le tournoi. Les stats détaillées valent null tant qu'elles n'ont pas été
 * importées ; {@code fictitiousFields} liste les champs dont les valeurs sont FICTIVES.
 */
public record PlayerTournamentStats(
        Integer playerId,
        String player,
        Integer teamId,
        String team,
        Integer matchesPlayed,
        Integer matchesStarted,
        Integer minutesPlayed,
        Integer goals,
        Integer penaltyGoals,
        Integer ownGoals,
        Integer yellowCards,
        Integer redCards,
        Integer foulsCommitted,
        Integer freeKicksWon,
        Integer freeKicksConceded,
        Integer duelsWon,
        Integer duelsLost,
        Integer corners,
        Integer offsides,
        BigDecimal distanceKm,
        Integer manOfTheMatchCount,
        Boolean manOfTheMatch,
        Integer detailedStatsMatches,
        BigDecimal rating,
        BigDecimal ratingWeighted,
        List<String> fictitiousFields) {

    /** Champs générés par la partie 6 de donneesStats.sql (stats détaillées fictives). */
    public static final List<String> FICTITIOUS_FIELDS = List.of(
            "foulsCommitted", "freeKicksWon", "freeKicksConceded", "duelsWon", "duelsLost", "corners", "offsides",
            "distanceKm", "manOfTheMatchCount", "manOfTheMatch");

    public PlayerTournamentStats {
        fictitiousFields = FICTITIOUS_FIELDS;
    }
}
