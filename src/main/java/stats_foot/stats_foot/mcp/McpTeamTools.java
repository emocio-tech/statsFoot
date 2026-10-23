package stats_foot.stats_foot.mcp;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import stats_foot.stats_foot.team.dto.TeamComparison;
import stats_foot.stats_foot.team.dto.TeamLookupResult;
import stats_foot.stats_foot.team.tool.TeamTools;

/** MCP tools exposing the team search and comparison; registered explicitly, not a Spring bean. */
public class McpTeamTools {

	private final TeamTools teamTools;

	public McpTeamTools(TeamTools teamTools) {
		this.teamTools = teamTools;
	}

	@Tool(name = "searchTeams", description = "Looks up a World Cup 2026 team by full or partial name "
			+ "(accent-insensitive) or FIFA code. Returns its full profile (wins, draws, losses, points, goals, cards, "
			+ "rating...) when the name identifies one team; otherwise returns the list of closest names "
			+ "(id, name, code) so you can ask which one is meant; fails if nobody matches.")
	public TeamLookupResult searchTeams(@ToolParam(description = "Full or partial team name, or FIFA code") String name) {
		return teamTools.searchTeams(name);
	}

	@Tool(name = "compareTeams", description = "Compare two World Cup 2026 teams and return a report. "
			+ "Each team is identified by its full or partial name (accent-insensitive) or FIFA code.")
	public TeamComparison compareTeams(
		@ToolParam(description = "Full or partial name (or FIFA code) of first team to compare") String teamA,
		@ToolParam(description = "Full or partial name (or FIFA code) of second team to compare") String teamB
	) {
		return teamTools.compareTeams(teamA, teamB);
	}

}
