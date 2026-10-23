package stats_foot.stats_foot.tool;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import stats_foot.stats_foot.player.util.NameNormalizer;

/**
 * LLM tools reading the tournament views: {@code stat} reads any player or team statistic (list, sum, average,
 * maximum, minimum, count), {@code rank} ranks one statistic with ties kept, {@code matches} lists the matches and
 * {@code calculate} does exact arithmetic. There is no free SQL: the statistic is picked from a closed list
 * ({@link StatMetrics}), never typed into a query.
 */
@Component
public class StatTools {

	static final int DEFAULT_LIMIT = 5;
	static final int MAX_LIMIT = 20;
	static final int RANK_TOP = 5;

	static final List<String> STAGES = List.of("GROUP", "ROUND_OF_32", "ROUND_OF_16", "QUARTER_FINAL", "SEMI_FINAL",
			"THIRD_PLACE", "FINAL");
	static final int MATCHES_DEFAULT_LIMIT = 20;
	static final int MAX_MATCHES = 104;

	static final List<String> AGGREGATES = List.of("NONE", "SUM", "AVG", "MAX", "MIN", "COUNT");

	private static final String COMPETITION = "competition_id = (SELECT MAX(id) FROM competition)";

	private static final String MATCH_SQL = """
			SELECT m.id, m.stage, m.group_name, m.kickoff_at, s.name AS stadium, s.city,
			       t1.name AS team1, t2.name AS team2,
			       COALESCE(m.et_team1, m.ft_team1) AS score1, COALESCE(m.et_team2, m.ft_team2) AS score2,
			       m.et_team1 IS NOT NULL AS extra_time,
			       m.pen_team1 AS penalties1, m.pen_team2 AS penalties2,
			       r1.rating AS rating1, r2.rating AS rating2
			FROM match m
			JOIN stadium s ON s.id = m.stadium_id
			JOIN team t1 ON t1.id = m.team1_id
			JOIN team t2 ON t2.id = m.team2_id
			LEFT JOIN team_match_rating r1 ON r1.match_id = m.id AND r1.team_id = m.team1_id
			LEFT JOIN team_match_rating r2 ON r2.match_id = m.id AND r2.team_id = m.team2_id
			WHERE m.competition_id = (SELECT MAX(id) FROM competition)
			ORDER BY m.kickoff_at, m.id
			""";

	private final JdbcClient jdbcClient;

	public StatTools(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	/** One line: a player or a team (name, team of the player) and the value of the statistic. */
	public record Row(String name, String team, BigDecimal value) {
	}

	public record StatResult(String scope, String metric, String aggregate, BigDecimal value, int considered,
			List<Row> rows, String note) {
	}

	/** One ranked line; ties share the same rank. */
	public record RankedEntry(int rank, String name, String team, BigDecimal value) {
	}

	public record RankResult(String scope, String metric, String unit, String order, String rule,
			List<RankedEntry> winners, List<RankedEntry> top) {
	}

	/** A match with its final score (extra time included) and the rating of each team. */
	public record MatchSummary(int id, String stage, String groupName, OffsetDateTime kickoffAt, String stadium,
			String city, String team1, String team2, Integer score1, Integer score2, boolean extraTime,
			Integer penalties1, Integer penalties2, BigDecimal rating1, BigDecimal rating2) {
	}

	@Tool(description = "Reads a player or team statistic of the tournament and lists, sums or averages it. "
			+ "Player statistics: matches_played, matches_started, minutes_played, goals, penalty_goals, own_goals, "
			+ "yellow_cards, red_cards, fouls_committed, free_kicks_won, free_kicks_conceded, duels_won, duels_lost, "
			+ "corners, offsides, distance_km, man_of_the_match_count, rating, rating_weighted. Team statistics: "
			+ "matches_played, wins, draws, losses, points, goals_for, goals_against, goal_difference, yellow_cards, "
			+ "red_cards, fair_play_points, fouls_committed, free_kicks_won, free_kicks_conceded, duels_won, "
			+ "duels_lost, corners, offsides, distance_km, rating. aggregate: NONE (ranked list, limit lines), SUM, "
			+ "AVG, MAX, MIN or COUNT over the kept lines. name and team filter (accents and case ignored; team only "
			+ "applies to a player). For any calculation between several results, use the calculate tool.")
	public StatResult stat(
			@ToolParam(description = "PLAYER or TEAM") String scope,
			@ToolParam(description = "Statistic name, see the tool description (e.g. goals, corners, goal_difference)")
			String metric,
			@ToolParam(description = "NONE, SUM, AVG, MAX, MIN or COUNT", required = false) String aggregate,
			@ToolParam(description = "Name (or start of name) of the player or team, to keep only it", required = false)
			String name,
			@ToolParam(description = "Team of the player, to keep only its squad (scope PLAYER)", required = false)
			String team,
			@ToolParam(description = "Sort of the list: DESC (default, highest first) or ASC", required = false)
			String order,
			@ToolParam(description = "Number of lines of the list, 5 by default, 20 at most", required = false)
			Integer limit) {
		boolean players = isPlayers(scope);
		String key = metric == null ? "" : metric.trim().toLowerCase(Locale.ROOT);
		String expression = expressionOf(players, key, metric);
		String agg = aggregate == null || aggregate.isBlank() ? "NONE" : aggregate.trim().toUpperCase(Locale.ROOT);
		if (!AGGREGATES.contains(agg)) {
			throw new IllegalArgumentException(
					"Unknown aggregate: " + aggregate + " (" + String.join(", ", AGGREGATES) + ").");
		}
		// expression and columns come from the closed lists above: never from user input
		List<Row> all = players
				? jdbcClient.sql("SELECT player AS name, team, CAST(" + expression + " AS NUMERIC) AS stat_value FROM "
						+ "v_player_tournament_stats WHERE " + COMPETITION)
						.query((rs, i) -> new Row(rs.getString("name"), rs.getString("team"),
								rs.getBigDecimal("stat_value")))
						.list()
				: jdbcClient.sql("SELECT team AS name, CAST(" + expression + " AS NUMERIC) AS stat_value FROM "
						+ "v_team_tournament_stats WHERE " + COMPETITION)
						.query((rs, i) -> new Row(rs.getString("name"), null, rs.getBigDecimal("stat_value")))
						.list();
		return compute(players ? "PLAYER" : "TEAM", key, agg, all, name, players ? team : null, order, limit);
	}

	@Tool(description = "Ranks a player or team statistic (top 5, ties kept) and gives the winner(s) and the rule "
			+ "applied. scope PLAYER or TEAM, metric among the statistics of the stat tool (e.g. goals, corners, "
			+ "goal_difference), order DESC (default, highest first) or ASC. Ties share a rank, so the top can have "
			+ "more than 5 lines. Unknown values are left out.")
	public RankResult rank(
			@ToolParam(description = "PLAYER or TEAM") String scope,
			@ToolParam(description = "Statistic to rank, same names as the stat tool (e.g. goals, yellow_cards, points)")
			String metric,
			@ToolParam(description = "DESC (default, highest first) or ASC", required = false) String order) {
		boolean players = isPlayers(scope);
		String key = metric == null ? "" : metric.trim().toLowerCase(Locale.ROOT);
		String expression = expressionOf(players, key, metric);
		String direction = "ASC".equalsIgnoreCase(order == null ? null : order.trim()) ? "ASC" : "DESC";
		// expression, view and columns come from closed lists: never from user input
		String sql = """
				SELECT * FROM (
				    SELECT %s AS name, %s AS team, CAST(%s AS NUMERIC) AS stat_value,
				           RANK() OVER (ORDER BY %s %s) AS stat_rank
				    FROM %s
				    WHERE %s AND %s IS NOT NULL
				) ranked
				WHERE stat_rank <= :top
				ORDER BY stat_rank, name
				""".formatted(players ? "player" : "team", players ? "team" : "NULL", expression, expression,
				direction, players ? "v_player_tournament_stats" : "v_team_tournament_stats", COMPETITION,
				expression);
		List<RankedEntry> top = jdbcClient.sql(sql)
				.param("top", RANK_TOP)
				.query((rs, i) -> new RankedEntry(rs.getInt("stat_rank"), rs.getString("name"), rs.getString("team"),
						rs.getBigDecimal("stat_value")))
				.list();
		String unit = StatMetrics.label(players, key);
		String rule = "Ranking by " + unit + ", " + ("ASC".equals(direction) ? "lowest" : "highest")
				+ " first; ties share the rank.";
		return new RankResult(players ? "PLAYER" : "TEAM", key, unit, direction, rule,
				top.stream().filter(e -> e.rank() == 1).toList(), top);
	}

	@Tool(description = "Lists the matches of the tournament in chronological order: date, stage (GROUP, ROUND_OF_32, "
			+ "ROUND_OF_16, QUARTER_FINAL, SEMI_FINAL, THIRD_PLACE, FINAL), group, stadium and city, teams, final score "
			+ "(extra time included, extraTime true if there was one), penalty shoot-out and rating of each team. "
			+ "team keeps only the matches of that team, stage only that stage (accents and case ignored for team); "
			+ "limit: 20 by default, 104 at most. Use it for questions about a score, a winner, a stadium or a schedule.")
	public List<MatchSummary> matches(
			@ToolParam(description = "Name (or start of name) of one of the two teams", required = false) String team,
			@ToolParam(description = "Stage: GROUP, ROUND_OF_32, ROUND_OF_16, QUARTER_FINAL, SEMI_FINAL, THIRD_PLACE or FINAL",
					required = false) String stage,
			@ToolParam(description = "Number of matches, 20 by default, 104 at most", required = false) Integer limit) {
		String wanted = stage == null || stage.isBlank() ? null : stage.trim().toUpperCase(Locale.ROOT);
		if (wanted != null && !STAGES.contains(wanted)) {
			throw new IllegalArgumentException("Unknown stage: " + stage + " (" + String.join(", ", STAGES) + ").");
		}
		int max = limit == null || limit < 1 ? MATCHES_DEFAULT_LIMIT : Math.min(limit, MAX_MATCHES);
		return jdbcClient.sql(MATCH_SQL)
				.query((rs, i) -> new MatchSummary(rs.getInt("id"), rs.getString("stage"), rs.getString("group_name"),
						rs.getObject("kickoff_at", OffsetDateTime.class), rs.getString("stadium"),
						rs.getString("city"), rs.getString("team1"), rs.getString("team2"),
						(Integer) rs.getObject("score1"), (Integer) rs.getObject("score2"),
						rs.getBoolean("extra_time"), (Integer) rs.getObject("penalties1"),
						(Integer) rs.getObject("penalties2"), rs.getBigDecimal("rating1"),
						rs.getBigDecimal("rating2")))
				.list()
				.stream()
				.filter(m -> wanted == null || wanted.equals(m.stage()))
				.filter(m -> team == null || team.isBlank() || matchesName(m.team1(), team)
						|| matchesName(m.team2(), team))
				.limit(max)
				.toList();
	}

	@Tool(description = "Exactly computes an arithmetic expression: decimal numbers, + - * / %, parentheses. "
			+ "Use it for any sum, difference, average, ratio or percentage from numbers obtained from the other "
			+ "tools, rather than computing mentally.")
	public String calculate(@ToolParam(description = "E.g. (14 + 9) / 2 or 100 * 7 / 8") String expression) {
		return Calculator.evaluate(expression).toPlainString();
	}

	private static boolean isPlayers(String scope) {
		boolean players = "PLAYER".equalsIgnoreCase(scope == null ? null : scope.trim());
		if (!players && !"TEAM".equalsIgnoreCase(scope == null ? null : scope.trim())) {
			throw new IllegalArgumentException("Unknown scope: " + scope + " (PLAYER or TEAM).");
		}
		return players;
	}

	private static String expressionOf(boolean players, String key, String metric) {
		String expression = StatMetrics.expression(players, key);
		if (expression == null) {
			throw new IllegalArgumentException("Unknown statistic: " + metric + ". Available: "
					+ String.join(", ", StatMetrics.names(players)) + ".");
		}
		return expression;
	}

	/** Filters, sorts and aggregates lines already read; no database access, so it can be tested alone. */
	static StatResult compute(String scope, String metric, String aggregate, List<Row> all, String name, String team,
			String order, Integer limit) {
		List<Row> rows = new ArrayList<>(all.stream()
				.filter(r -> matchesName(r.name(), name))
				.filter(r -> matchesName(r.team(), team))
				.toList());
		int considered = rows.size();
		if ("NONE".equals(aggregate)) {
			// unknown values always at the end of the list, whatever the sort direction
			Comparator<BigDecimal> direction = "ASC".equalsIgnoreCase(order) ? Comparator.naturalOrder()
					: Comparator.reverseOrder();
			rows.sort(Comparator.comparing(Row::value, Comparator.nullsLast(direction)));
			int max = limit == null || limit < 1 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
			return new StatResult(scope, metric, aggregate, null, considered,
					List.copyOf(rows.size() > max ? rows.subList(0, max) : rows), null);
		}
		List<BigDecimal> values = rows.stream().map(Row::value).filter(v -> v != null).toList();
		BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal result = switch (aggregate) {
			case "COUNT" -> BigDecimal.valueOf(values.size());
			case "SUM" -> sum;
			case "AVG" -> values.isEmpty() ? null
					: sum.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP).stripTrailingZeros();
			case "MAX" -> values.stream().max(Comparator.naturalOrder()).orElse(null);
			default -> values.stream().min(Comparator.naturalOrder()).orElse(null);
		};
		String note = values.isEmpty() && !"COUNT".equals(aggregate) ? "no value on the kept lines" : null;
		return new StatResult(scope, metric, aggregate, result, considered, List.of(), note);
	}

	/** Empty filter: everything passes; otherwise each typed word must start a word of the name (accents/case ignored). */
	static boolean matchesName(String value, String filter) {
		if (filter == null || filter.isBlank()) {
			return true;
		}
		if (value == null) {
			return false;
		}
		List<String> valueWords = Arrays.asList(words(value));
		return Arrays.stream(words(filter))
				.allMatch(w -> valueWords.stream().anyMatch(v -> v.equals(w) || (w.length() >= 2 && v.startsWith(w))));
	}

	private static String[] words(String text) {
		return NameNormalizer.normalize(text).replaceAll("[^a-z0-9]+", " ").trim().split(" ");
	}

}
