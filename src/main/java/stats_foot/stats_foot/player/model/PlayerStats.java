package stats_foot.stats_foot.player.model;

/** Tournament statistics of a player. A {@code null} value means "not available", distinct from {@code 0}. */
public record PlayerStats(
		Integer appearances,
		Integer starts,
		Integer minutesPlayed,
		Integer goals,
		Integer assists,
		Integer ownGoals,
		Integer shots,
		Integer shotsOnTarget,
		Integer yellowCards,
		Integer redCards,
		Integer foulsCommitted,
		Integer foulsSuffered,
		Integer passes,
		Double passAccuracy) {
}
