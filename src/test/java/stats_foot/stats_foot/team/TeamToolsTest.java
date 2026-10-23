package stats_foot.stats_foot.team;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import stats_foot.stats_foot.team.dto.TeamComparison;
import stats_foot.stats_foot.team.dto.TeamLookupResult;
import stats_foot.stats_foot.team.dto.TeamProfile;
import stats_foot.stats_foot.team.dto.TeamSearchResult;
import stats_foot.stats_foot.team.dto.TeamSummary;
import stats_foot.stats_foot.team.exception.TeamNotFoundException;
import stats_foot.stats_foot.team.service.TeamComparisonService;
import stats_foot.stats_foot.team.service.TeamService;
import stats_foot.stats_foot.team.tool.TeamTools;

class TeamToolsTest {

	private final TeamService teamService = mock(TeamService.class);
	private final TeamComparisonService teamComparisonService = mock(TeamComparisonService.class);
	private final TeamTools tools = new TeamTools(teamService, teamComparisonService);

	private static TeamSearchResult found(TeamSummary... teams) {
		return new TeamSearchResult(List.of(teams), teams.length);
	}

	@Test
	void searchTeams_singleMatch_returnsProfile() {
		TeamProfile profile = mock(TeamProfile.class);
		when(teamService.search("france")).thenReturn(found(new TeamSummary(7, "France", "FRA")));
		when(teamService.getProfile(7)).thenReturn(profile);

		TeamLookupResult result = tools.searchTeams("france");

		assertSame(profile, result.profile());
		assertNull(result.candidates());
	}

	@Test
	void searchTeams_exactNameAmongSeveral_returnsProfile() {
		TeamProfile profile = mock(TeamProfile.class);
		when(teamService.search("Guinea")).thenReturn(
				found(new TeamSummary(1, "Guinea", "GUI"), new TeamSummary(2, "Equatorial Guinea", "EQG")));
		when(teamService.getProfile(1)).thenReturn(profile);

		assertSame(profile, tools.searchTeams("Guinea").profile());
	}

	@Test
	void searchTeams_severalClosestNames_returnsCandidates() {
		TeamSearchResult search = found(new TeamSummary(1, "Korea Republic", "KOR"),
				new TeamSummary(2, "Korea DPR", "PRK"));
		when(teamService.search("korea")).thenReturn(search);

		TeamLookupResult result = tools.searchTeams("korea");

		assertSame(search, result.candidates());
		assertNull(result.profile());
	}

	@Test
	void searchTeams_noMatch_throwsTeamNotFound() {
		when(teamService.search("zzz")).thenReturn(found());

		assertThrows(TeamNotFoundException.class, () -> tools.searchTeams("zzz"));
	}

	@Test
	void compareTeams_delegatesToService() {
		TeamComparison comparison = mock(TeamComparison.class);
		when(teamComparisonService.compareByName("france", "brazil")).thenReturn(comparison);

		assertSame(comparison, tools.compareTeams("france", "brazil"));
	}

	@Test
	void compareTeams_teamNotFound_tellsToUseSearchTeams() {
		when(teamComparisonService.compareByName("france", "unknown"))
				.thenThrow(new TeamNotFoundException("Team \"unknown\" not found."));

		TeamNotFoundException e = assertThrows(TeamNotFoundException.class,
				() -> tools.compareTeams("france", "unknown"));

		assertTrue(e.getMessage().startsWith("Team \"unknown\" not found."));
		assertTrue(e.getMessage().contains("searchTeams"));
	}
}
