package stats_foot.stats_foot.player.tool;

import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import stats_foot.stats_foot.player.dto.PlayerComparison;
import stats_foot.stats_foot.player.dto.PlayerLookupResult;
import stats_foot.stats_foot.player.dto.PlayerQueryResult;
import stats_foot.stats_foot.player.dto.PlayerSearchResult;
import stats_foot.stats_foot.player.dto.PlayerSummary;
import stats_foot.stats_foot.player.exception.ComparisonPlayerNotFoundException;
import stats_foot.stats_foot.player.exception.PlayerNotFoundException;
import stats_foot.stats_foot.player.model.ComparisonOperator;
import stats_foot.stats_foot.player.model.PlayerStat;
import stats_foot.stats_foot.player.service.PlayerComparisonService;
import stats_foot.stats_foot.player.service.PlayerService;
import stats_foot.stats_foot.player.util.NameNormalizer;

/** LLM tools exposing the player data, backed by the same service as the /api/players endpoints. */
@Component
public class PlayerTools {

	private final PlayerService playerService;
	private final PlayerComparisonService playerComparisonService;

	public PlayerTools(PlayerService playerService, PlayerComparisonService playerComparisonService) {
		this.playerService = playerService;
		this.playerComparisonService = playerComparisonService;
	}

	@Tool(description = "Looks up a World Cup 2026 player by full or partial name (accent-insensitive). "
			+ "Returns his full profile (team, goals, cards, minutes, rating...) when the name identifies one player; "
			+ "otherwise returns the list of closest names (id, name, team) so you can ask the user which one; "
			+ "fails if nobody matches.")
	public PlayerLookupResult searchPlayers(@ToolParam(description = "Full or partial player name") String name) {
		PlayerSearchResult result = playerService.search(name);
		List<PlayerSummary> players = result.players();
		if (players.isEmpty()) {
			throw new PlayerNotFoundException(PlayerService.NOT_FOUND_MESSAGE);
		}
		if (players.size() == 1) {
			return PlayerLookupResult.ofProfile(playerService.getProfile(players.get(0).id()));
		}
		String wanted = NameNormalizer.normalize(name);
		List<PlayerSummary> exact = players.stream()
				.filter(p -> NameNormalizer.normalize(p.name()).equals(wanted))
				.toList();
		if (exact.size() == 1) {
			return PlayerLookupResult.ofProfile(playerService.getProfile(exact.get(0).id()));
		}
		return PlayerLookupResult.ofCandidates(result);
	}

	@Tool(description = "Finds World Cup 2026 players from one of their attributes. All parameters are optional and "
			+ "combine with AND. Examples: all players of France -> team='France'; players with exactly 3 goals -> "
			+ "filterStat=GOALS, operator=EQUALS, value=3; most yellow cards -> sortBy=YELLOW_CARDS, descending=true, "
			+ "limit=5; all captains -> captainsOnly=true; least fair-play player -> sortBy=TOTAL_CARDS "
			+ "(yellow + red cards), descending=true. Players whose statistic is unavailable are left out. "
			+ "Ties are possible: ask for a few results (limit) and compare the sortValue of each to report ties. "
			+ "Returns at most 50 players (10 by default), the total number of matches and whether the list was cut.")
	public PlayerQueryResult findPlayers(
			@ToolParam(required = false, description = "Team name or FIFA code, e.g. 'France' or 'FRA'") String team,
			@ToolParam(required = false, description = "true to keep only players who captained at least one match") Boolean captainsOnly,
			@ToolParam(required = false, description = "Statistic to filter on; requires operator and value") PlayerStat filterStat,
			@ToolParam(required = false, description = "Comparison applied to filterStat") ComparisonOperator operator,
			@ToolParam(required = false, description = "Reference value of the comparison") Double value,
			@ToolParam(required = false, description = "Statistic to sort on; its value is returned as sortValue") PlayerStat sortBy,
			@ToolParam(required = false, description = "true (default) for highest values first, false for lowest first") Boolean descending,
			@ToolParam(required = false, description = "Maximum number of players returned (default 10, max 50)") Integer limit) {
		return playerService.query(team, captainsOnly, filterStat, operator, value, sortBy,
				!Boolean.FALSE.equals(descending), limit);
	}

	@Tool(description = "Compare two players and return a report. If a player is not found, "
			+ "call searchPlayers to check whether he exists and under which name, then retry.")
	public PlayerComparison comparePlayers(
		@ToolParam(description = "Full or partial name of first player to compare") String playerA,
		@ToolParam(description = "Full or partial name of second player to compare") String playerB
	) {
		try {
			return playerComparisonService.compareByName(playerA, playerB);
		} catch (ComparisonPlayerNotFoundException e) {
			throw new ComparisonPlayerNotFoundException(e.getMessage()
					+ " Call the searchPlayers tool with this player's name to check whether he exists "
					+ "and get his exact name, then call comparePlayers again.");
		}
	}

}
