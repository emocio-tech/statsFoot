package com.emocio.analysefoot.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Totaux d'une équipe sur le tournoi. Les stats détaillées valent null tant qu'elles n'ont pas été
 * importées ; {@code fictitiousFields} liste les champs dont les valeurs sont FICTIVES.
 */
public record TeamTournamentStats(
        Integer teamId,
        String team,
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
        Integer detailedStatsMatches,
        BigDecimal rating,
        List<String> fictitiousFields) {

    /** Champs générés par la partie 6 de donneesStats.sql (stats détaillées fictives). */
    public static final List<String> FICTITIOUS_FIELDS = List.of(
            "foulsCommitted", "freeKicksWon", "freeKicksConceded", "duelsWon", "duelsLost", "corners", "offsides",
            "distanceKm");

    public TeamTournamentStats {
        fictitiousFields = FICTITIOUS_FIELDS;
    }
}
