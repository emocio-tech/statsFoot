package stats_foot.stats_foot.player.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import stats_foot.stats_foot.player.dto.PlayerProfile;
import stats_foot.stats_foot.player.dto.PlayerSearchResult;
import stats_foot.stats_foot.player.service.PlayerService;

/** LLM tools exposing the player data, backed by the same service as the /api/players endpoints. */
@Component
public class PlayerTools {

	private final PlayerService playerService;

	public PlayerTools(PlayerService playerService) {
		this.playerService = playerService;
	}

	@Tool(description = "Searches World Cup 2026 players by name (partial, accent-insensitive). "
			+ "Returns at most 50 matches with their id, name and team. Use it to find a player id.")
	public PlayerSearchResult searchPlayers(@ToolParam(description = "Full or partial player name") String name) {
		return playerService.search(name);
	}

	@Tool(description = "Gets the full World Cup 2026 profile of a player (team, goals, cards, minutes, rating...) "
			+ "from his id. Call searchPlayers first if the id is unknown.")
	public PlayerProfile getPlayerProfile(@ToolParam(description = "Player id returned by searchPlayers") int id) {
		return playerService.getProfile(id);
	}

}
