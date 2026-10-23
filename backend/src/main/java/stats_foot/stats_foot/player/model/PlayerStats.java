package stats_foot.stats_foot.player.model;

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
		Double ratingWeighted) {
}
