package stats_foot.stats_foot.player.dto;

import java.util.List;

/** Outcome of a player query: the returned players, the full number of matches and whether the list was cut. */
public record PlayerQueryResult(List<PlayerQueryMatch> players, int total, boolean truncated) {

	/** A matching player; {@code sortValue} is the value of the sort statistic, {@code null} if none was requested. */
	public record PlayerQueryMatch(int id, String name, String teamCode, String teamName, int captainMatches,
			Double sortValue) {
	}

}
