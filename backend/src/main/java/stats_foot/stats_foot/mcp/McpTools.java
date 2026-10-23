package stats_foot.stats_foot.mcp;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import stats_foot.stats_foot.player.dto.PlayerComparison;
import stats_foot.stats_foot.player.dto.PlayerLookupResult;
import stats_foot.stats_foot.player.exception.ComparisonPlayerNotFoundException;
import stats_foot.stats_foot.player.service.PlayerComparisonService;
import stats_foot.stats_foot.player.tool.PlayerTools;

/** MCP tools exposing the player search and comparison; registered explicitly, not a Spring bean. */
public class McpTools {

	private final PlayerComparisonService playerComparisonService;
	private final PlayerTools playerTools;

	public McpTools(PlayerComparisonService playerComparisonService, PlayerTools playerTools) {
		this.playerComparisonService = playerComparisonService;
		this.playerTools = playerTools;
	}

	@Tool(name = "searchPlayers", description = "Looks up a World Cup 2026 player by full or partial name "
			+ "(accent-insensitive). Returns his full profile (team, goals, cards, minutes, rating...) when the name "
			+ "identifies one player; otherwise returns the list of closest names (id, name, team) so you can ask "
			+ "which one is meant; fails if nobody matches.")
	public PlayerLookupResult searchPlayers(@ToolParam(description = "Full or partial player name") String name) {
		return playerTools.searchPlayers(name);
	}

	@Tool(name = "comparePlayers", description = "Compare two World Cup 2026 players and return a report. "
			+ "Each player is identified by his full or partial name (accent-insensitive).")
	public PlayerComparison comparePlayers(
		@ToolParam(description = "Full or partial name of first player to compare") String playerA,
		@ToolParam(description = "Full or partial name of second player to compare") String playerB
	) {
		try {
			return playerComparisonService.compareByName(playerA, playerB);
		} catch (ComparisonPlayerNotFoundException e) {
			throw new ComparisonPlayerNotFoundException(
					e.getMessage() + " Try again with a fuller or corrected full name.");
		}
	}

}
