package com.emocio.analysefoot.dto;

import java.math.BigDecimal;

/** Totaux d'une équipe sur le tournoi. Les stats détaillées valent null tant qu'elles n'ont pas été importées. */
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
        BigDecimal rating) {
}
