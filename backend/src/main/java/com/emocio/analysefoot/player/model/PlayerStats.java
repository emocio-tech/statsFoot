package com.emocio.analysefoot.player.model;

/** Tournament totals of a player. A {@code null} value means "not available", distinct from {@code 0}. */
public record PlayerStats(
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
		Double distanceKm,
		Integer manOfTheMatchCount,
		Double rating,
		Double ratingWeighted,
		Integer assists,
		Integer decisiveGoals,
		Integer secondYellows,
		Integer directReds,
		Integer fairPlayPoints) {

	/** Statistics without the assists, decisive goals and discipline details (all {@code null}). */
	public PlayerStats(Integer matchesPlayed, Integer matchesStarted, Integer minutesPlayed, Integer goals,
			Integer penaltyGoals, Integer ownGoals, Integer yellowCards, Integer redCards, Integer foulsCommitted,
			Integer freeKicksWon, Integer freeKicksConceded, Integer duelsWon, Integer duelsLost, Integer corners,
			Integer offsides, Double distanceKm, Integer manOfTheMatchCount, Double rating, Double ratingWeighted) {
		this(matchesPlayed, matchesStarted, minutesPlayed, goals, penaltyGoals, ownGoals, yellowCards, redCards,
				foulsCommitted, freeKicksWon, freeKicksConceded, duelsWon, duelsLost, corners, offsides, distanceKm,
				manOfTheMatchCount, rating, ratingWeighted, null, null, null, null, null);
	}

}
