package stats_foot.stats_foot.team.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import stats_foot.stats_foot.team.dto.TeamComparison;
import stats_foot.stats_foot.team.dto.TeamSearchResult;
import stats_foot.stats_foot.team.service.TeamComparisonService;
import stats_foot.stats_foot.team.service.TeamService;

/** REST endpoints of the team feature. */
@RestController
@RequestMapping("/api/teams")
public class TeamController {

	private final TeamService teamService;

	private final TeamComparisonService teamComparisonService;

	public TeamController(TeamService teamService, TeamComparisonService teamComparisonService) {
		this.teamService = teamService;
		this.teamComparisonService = teamComparisonService;
	}

	@GetMapping
	public TeamSearchResult search(@RequestParam("name") String name) {
		return teamService.search(name);
	}

	@GetMapping("/compare")
	public TeamComparison compare(@RequestParam("teamA") int teamA, @RequestParam("teamB") int teamB) {
		return teamComparisonService.compare(teamA, teamB);
	}

}
