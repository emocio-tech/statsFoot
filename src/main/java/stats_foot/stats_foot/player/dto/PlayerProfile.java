package stats_foot.stats_foot.player.dto;

/**
 * A player profile as exposed by the API. Totals keep {@code null} (kept in the JSON) when not available.
 */
public record PlayerProfile(
		int id,
		String name,
		String teamCode,
		String teamName,
		int goalkeeperMatches,
		int captainMatches,
		PlayerProfileStats stats) {

	/** Tournament totals of the player. */
	public record PlayerProfileStats(
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

}
