package stats_foot.stats_foot.team.tool;

import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import stats_foot.stats_foot.player.util.NameNormalizer;
import stats_foot.stats_foot.team.dto.TeamComparison;
import stats_foot.stats_foot.team.dto.TeamLookupResult;
import stats_foot.stats_foot.team.dto.TeamSearchResult;
import stats_foot.stats_foot.team.dto.TeamSummary;
import stats_foot.stats_foot.team.exception.TeamNotFoundException;
import stats_foot.stats_foot.team.service.TeamComparisonService;
import stats_foot.stats_foot.team.service.TeamService;

/** LLM tools exposing the team data, backed by the same services as the /api/teams endpoints. */
@Component
public class TeamTools {

	private final TeamService teamService;
	private final TeamComparisonService teamComparisonService;

	public TeamTools(TeamService teamService, TeamComparisonService teamComparisonService) {
		this.teamService = teamService;
		this.teamComparisonService = teamComparisonService;
	}

	@Tool(description = "Looks up a World Cup 2026 team by full or partial name (accent-insensitive) or FIFA code. "
			+ "Returns its full profile (wins, draws, losses, points, goals, cards, fair-play points, rating...) when the "
			+ "name identifies one team; otherwise returns the list of closest names (id, name, code) so you can ask "
			+ "the user which one; fails if nobody matches.")
	public TeamLookupResult searchTeams(@ToolParam(description = "Full or partial team name, or FIFA code") String name) {
		TeamSearchResult result = teamService.search(name);
		List<TeamSummary> teams = result.teams();
		if (teams.isEmpty()) {
			throw new TeamNotFoundException(TeamService.NOT_FOUND_MESSAGE);
		}
		if (teams.size() == 1) {
			return TeamLookupResult.ofProfile(teamService.getProfile(teams.get(0).id()));
		}
		String wanted = NameNormalizer.normalize(name);
		List<TeamSummary> exact = teams.stream()
				.filter(t -> NameNormalizer.normalize(t.name()).equals(wanted))
				.toList();
		if (exact.size() == 1) {
			return TeamLookupResult.ofProfile(teamService.getProfile(exact.get(0).id()));
		}
		return TeamLookupResult.ofCandidates(result);
	}

	@Tool(description = "Compare two teams and return a report. If a team is not found, "
			+ "call searchTeams to check whether it exists and under which name, then retry.")
	public TeamComparison compareTeams(
		@ToolParam(description = "Full or partial name (or FIFA code) of first team to compare") String teamA,
		@ToolParam(description = "Full or partial name (or FIFA code) of second team to compare") String teamB
	) {
		try {
			return teamComparisonService.compareByName(teamA, teamB);
		} catch (TeamNotFoundException e) {
			throw new TeamNotFoundException(e.getMessage()
					+ " Call the searchTeams tool with this team's name to check whether it exists "
					+ "and get its exact name, then call compareTeams again.");
		}
	}

}
