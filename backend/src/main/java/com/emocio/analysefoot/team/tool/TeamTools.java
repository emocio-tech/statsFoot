package com.emocio.analysefoot.team.tool;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import com.emocio.analysefoot.player.util.NameNormalizer;
import com.emocio.analysefoot.team.dto.TeamComparison;
import com.emocio.analysefoot.team.dto.TeamSearchResult;
import com.emocio.analysefoot.team.dto.TeamSummary;
import com.emocio.analysefoot.team.exception.TeamNotFoundException;
import com.emocio.analysefoot.team.service.TeamComparisonService;
import com.emocio.analysefoot.team.service.TeamService;

/** LLM tools exposing the team data, backed by the same services as the player tools. */
@Component
public class TeamTools {

	private final TeamService teamService;
	private final TeamComparisonService teamComparisonService;

	public TeamTools(TeamService teamService, TeamComparisonService teamComparisonService) {
		this.teamService = teamService;
		this.teamComparisonService = teamComparisonService;
	}

	@Tool(description = "Compare two World Cup 2026 teams statistic by statistic (results, goals, discipline, "
			+ "fair play, rating, final position...) and return a report with the leader of each line and a verdict. "
			+ "Each team is identified by its name or FIFA code (accent-insensitive), e.g. 'France' or 'FRA'. "
			+ "Fails with the list of candidates when a name matches several teams, or when nobody matches.")
	public TeamComparison compareTeams(
			@ToolParam(description = "Name or FIFA code of the first team") String teamA,
			@ToolParam(description = "Name or FIFA code of the second team") String teamB) {
		return teamComparisonService.compare(resolve(teamA), resolve(teamB));
	}

	private int resolve(String name) {
		TeamSearchResult result = teamService.search(name);
		List<TeamSummary> teams = result.teams();
		if (teams.isEmpty()) {
			throw new TeamNotFoundException("Team \"" + name + "\" not found.");
		}
		if (teams.size() == 1) {
			return teams.get(0).id();
		}
		String wanted = NameNormalizer.normalize(name);
		List<TeamSummary> exact = teams.stream()
				.filter(t -> NameNormalizer.normalize(t.name()).equals(wanted)
						|| NameNormalizer.normalize(t.code()).equals(wanted))
				.toList();
		if (exact.size() == 1) {
			return exact.get(0).id();
		}
		String candidates = teams.stream().map(t -> t.name() + " (" + t.code() + ")").collect(Collectors.joining(", "));
		throw new IllegalArgumentException("Several teams match \"" + name + "\": " + candidates
				+ ". Please be more precise.");
	}

}
