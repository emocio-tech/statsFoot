package stats_foot.stats_foot.mcp;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import stats_foot.stats_foot.player.dto.PlayerComparison;
import stats_foot.stats_foot.player.exception.ComparisonPlayerNotFoundException;
import stats_foot.stats_foot.player.service.PlayerComparisonService;

/** MCP tool exposing the player comparison; registered explicitly, not a Spring bean. */
public class McpTools {

	private final PlayerComparisonService playerComparisonService;

	public McpTools(PlayerComparisonService playerComparisonService) {
		this.playerComparisonService = playerComparisonService;
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
