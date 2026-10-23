package stats_foot.stats_foot.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The player and team statistics readable by the {@code stat} and {@code rank} tools: a closed list, each with its
 * SQL expression on the tournament views. What enters a query always comes from here, never from user input.
 */
public final class StatMetrics {

	/** Player statistics (columns of v_player_tournament_stats) with their label. */
	private static final Map<String, String> PLAYER = new LinkedHashMap<>();

	/** Team statistics (columns of v_team_tournament_stats, plus the goal difference) with their label. */
	private static final Map<String, String> TEAM = new LinkedHashMap<>();

	/** SQL expression of the team statistics that are not a column. */
	private static final Map<String, String> TEAM_EXPRESSION = Map.of("goal_difference", "goals_for - goals_against");

	static {
		PLAYER.put("matches_played", "matches played");
		PLAYER.put("matches_started", "matches started");
		PLAYER.put("minutes_played", "minutes played");
		PLAYER.put("goals", "goals");
		PLAYER.put("penalty_goals", "penalty goals");
		PLAYER.put("own_goals", "own goals");
		PLAYER.put("yellow_cards", "yellow cards");
		PLAYER.put("red_cards", "red cards");
		PLAYER.put("fouls_committed", "fouls committed");
		PLAYER.put("free_kicks_won", "free kicks won");
		PLAYER.put("free_kicks_conceded", "free kicks conceded");
		PLAYER.put("duels_won", "duels won");
		PLAYER.put("duels_lost", "duels lost");
		PLAYER.put("corners", "corners");
		PLAYER.put("offsides", "offsides");
		PLAYER.put("distance_km", "km covered");
		PLAYER.put("man_of_the_match_count", "man of the match awards");
		PLAYER.put("rating", "average rating");
		PLAYER.put("rating_weighted", "rating weighted by playing time");

		TEAM.put("matches_played", "matches played");
		TEAM.put("wins", "wins");
		TEAM.put("draws", "draws");
		TEAM.put("losses", "losses");
		TEAM.put("points", "points");
		TEAM.put("goals_for", "goals scored");
		TEAM.put("goals_against", "goals conceded");
		TEAM.put("goal_difference", "goal difference");
		TEAM.put("yellow_cards", "yellow cards");
		TEAM.put("red_cards", "red cards");
		TEAM.put("fair_play_points", "fair-play points");
		TEAM.put("fouls_committed", "fouls committed");
		TEAM.put("free_kicks_won", "free kicks won");
		TEAM.put("free_kicks_conceded", "free kicks conceded");
		TEAM.put("duels_won", "duels won");
		TEAM.put("duels_lost", "duels lost");
		TEAM.put("corners", "corners");
		TEAM.put("offsides", "offsides");
		TEAM.put("distance_km", "km covered");
		TEAM.put("rating", "average rating");
	}

	private StatMetrics() {
	}

	/** Names of the statistics of a scope ({@code PLAYER} when {@code players} is true, else {@code TEAM}). */
	public static List<String> names(boolean players) {
		return List.copyOf((players ? PLAYER : TEAM).keySet());
	}

	/** SQL expression of the statistic, or {@code null} if it does not exist for this scope. */
	public static String expression(boolean players, String metric) {
		if (metric == null || !(players ? PLAYER : TEAM).containsKey(metric)) {
			return null;
		}
		return players ? metric : TEAM_EXPRESSION.getOrDefault(metric, metric);
	}

	/** Label (unit) of the statistic; its name if unknown. */
	public static String label(boolean players, String metric) {
		return (players ? PLAYER : TEAM).getOrDefault(metric, metric);
	}

}
