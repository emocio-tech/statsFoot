package com.emocio.analysefoot.team.model;

/** Tournament totals of a team. A {@code null} value means "not available", distinct from {@code 0}. */
public record TeamStats(
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
		Double distanceKm,
		Double rating,
		Integer finalPosition) {

	/** Statistics without the final position ({@code null}). */
	public TeamStats(Integer matchesPlayed, Integer wins, Integer draws, Integer losses, Integer points,
			Integer goalsFor, Integer goalsAgainst, Integer yellowCards, Integer redCards, Integer fairPlayPoints,
			Integer foulsCommitted, Integer freeKicksWon, Integer freeKicksConceded, Integer duelsWon,
			Integer duelsLost, Integer corners, Integer offsides, Double distanceKm, Double rating) {
		this(matchesPlayed, wins, draws, losses, points, goalsFor, goalsAgainst, yellowCards, redCards,
				fairPlayPoints, foulsCommitted, freeKicksWon, freeKicksConceded, duelsWon, duelsLost, corners,
				offsides, distanceKm, rating, null);
	}

}
