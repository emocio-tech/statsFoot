package com.emocio.analysefoot.dto;

import java.math.BigDecimal;

/** Totaux d'un joueur sur le tournoi. Les stats détaillées valent null tant qu'elles n'ont pas été importées. */
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
        BigDecimal ratingWeighted) {
}
