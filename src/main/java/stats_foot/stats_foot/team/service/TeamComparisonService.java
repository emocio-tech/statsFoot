package stats_foot.stats_foot.team.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import stats_foot.stats_foot.player.exception.InvalidSearchException;
import stats_foot.stats_foot.player.util.NameNormalizer;
import stats_foot.stats_foot.team.dto.TeamSearchResult;
import stats_foot.stats_foot.team.dto.TeamSummary;
import stats_foot.stats_foot.team.exception.AmbiguousTeamNameException;
import stats_foot.stats_foot.player.dto.PlayerComparison.ComparisonLine;
import stats_foot.stats_foot.player.dto.PlayerComparison.ComparisonSummary;
import stats_foot.stats_foot.player.model.Team;
import stats_foot.stats_foot.player.service.ComparisonLines;
import stats_foot.stats_foot.player.service.ComparisonLines.Direction;
import stats_foot.stats_foot.player.service.ComparisonLines.StatDefinition;
import stats_foot.stats_foot.player.service.ComparisonVerdict;
import stats_foot.stats_foot.team.dto.TeamComparison;
import stats_foot.stats_foot.team.dto.TeamComparison.ComparedTeam;
import stats_foot.stats_foot.team.exception.SameTeamComparisonException;
import stats_foot.stats_foot.team.exception.TeamNotFoundException;
import stats_foot.stats_foot.team.model.TeamStats;
import stats_foot.stats_foot.team.model.TeamWithStats;
import stats_foot.stats_foot.team.repository.TeamRepository;

/** Compares two teams statistic by statistic. Free of any HTTP dependency. */
@Service
public class TeamComparisonService {

	private static final List<StatDefinition<TeamStats>> STATISTICS = List.of(
			new StatDefinition<>("matchesPlayed", "Matches played", Direction.NO_LEADER, TeamStats::matchesPlayed),
			new StatDefinition<>("wins", "Wins", Direction.HIGHER_BETTER, TeamStats::wins),
			new StatDefinition<>("draws", "Draws", Direction.NO_LEADER, TeamStats::draws),
			new StatDefinition<>("losses", "Losses", Direction.LOWER_BETTER, TeamStats::losses),
			new StatDefinition<>("points", "Points", Direction.HIGHER_BETTER, TeamStats::points),
			new StatDefinition<>("goalsFor", "Goals for", Direction.HIGHER_BETTER, TeamStats::goalsFor),
			new StatDefinition<>("goalsAgainst", "Goals against", Direction.LOWER_BETTER, TeamStats::goalsAgainst),
			new StatDefinition<>("yellowCards", "Yellow cards", Direction.LOWER_BETTER, TeamStats::yellowCards),
			new StatDefinition<>("redCards", "Red cards", Direction.LOWER_BETTER, TeamStats::redCards),
			new StatDefinition<>("fairPlayPoints", "Fair-play points", Direction.HIGHER_BETTER, TeamStats::fairPlayPoints),
			new StatDefinition<>("foulsCommitted", "Fouls committed", Direction.LOWER_BETTER, TeamStats::foulsCommitted),
			new StatDefinition<>("freeKicksWon", "Free kicks won", Direction.HIGHER_BETTER, TeamStats::freeKicksWon),
			new StatDefinition<>("freeKicksConceded", "Free kicks conceded", Direction.LOWER_BETTER, TeamStats::freeKicksConceded),
			new StatDefinition<>("duelsWon", "Duels won", Direction.HIGHER_BETTER, TeamStats::duelsWon),
			new StatDefinition<>("duelsLost", "Duels lost", Direction.LOWER_BETTER, TeamStats::duelsLost),
			new StatDefinition<>("corners", "Corners", Direction.HIGHER_BETTER, TeamStats::corners),
			new StatDefinition<>("offsides", "Offsides", Direction.LOWER_BETTER, TeamStats::offsides),
			new StatDefinition<>("distanceKm", "Distance (km)", Direction.NO_LEADER, TeamStats::distanceKm),
			new StatDefinition<>("rating", "Rating", Direction.HIGHER_BETTER, TeamStats::rating));

	private final TeamRepository teamRepository;

	private final TeamService teamService;

	public TeamComparisonService(TeamRepository teamRepository, TeamService teamService) {
		this.teamRepository = teamRepository;
		this.teamService = teamService;
	}

	/**
	 * Compares two teams designated by name or FIFA code. Each one is resolved with the team search (A first, then B).
	 *
	 * @throws InvalidSearchException       if a name is blank
	 * @throws TeamNotFoundException        if a name matches no team
	 * @throws AmbiguousTeamNameException   if a name matches several teams
	 * @throws SameTeamComparisonException  if both names resolve to the same team
	 */
	public TeamComparison compareByName(String nameA, String nameB) {
		int idA = resolve(nameA, "A");
		int idB = resolve(nameB, "B");
		return compare(idA, idB);
	}

	private int resolve(String name, String label) {
		TeamSearchResult result;
		try {
			result = teamService.search(name);
		} catch (InvalidSearchException e) {
			throw new InvalidSearchException("Team " + label + " name must not be blank.");
		}
		List<TeamSummary> teams = result.teams();
		if (teams.isEmpty()) {
			throw new TeamNotFoundException("Team \"" + name + "\" not found.");
		}
		if (teams.size() == 1) {
			return teams.get(0).id();
		}
		String wanted = NameNormalizer.normalize(name);
		List<TeamSummary> exact = teams.stream()
				.filter(t -> NameNormalizer.normalize(t.name()).equals(wanted))
				.toList();
		if (exact.size() == 1) {
			return exact.get(0).id();
		}
		String candidates = teams.stream().map(t -> t.name() + " (" + t.code() + ")")
				.collect(Collectors.joining(", "));
		throw new AmbiguousTeamNameException("Several teams match the name \"" + name + "\": " + candidates
				+ ". Please be more precise.");
	}

	/**
	 * Compares two teams.
	 *
	 * @param idA the id of the first team
	 * @param idB the id of the second team
	 * @return the comparison report
	 * @throws SameTeamComparisonException if both ids are equal
	 * @throws TeamNotFoundException       if a team does not exist (A is named first)
	 */
	public TeamComparison compare(int idA, int idB) {
		if (idA == idB) {
			throw new SameTeamComparisonException();
		}
		TeamWithStats teamA = teamRepository.findById(idA)
				.orElseThrow(() -> new TeamNotFoundException("Team A not found."));
		TeamWithStats teamB = teamRepository.findById(idB)
				.orElseThrow(() -> new TeamNotFoundException("Team B not found."));

		List<ComparisonLine> lines = ComparisonLines.build(STATISTICS, teamA.stats(), teamB.stats());
		ComparisonSummary summary = ComparisonLines.summarize(lines);
		String verdict = ComparisonVerdict.build(teamA.team().name(), teamB.team().name(), lines, summary, null);
		return new TeamComparison(toComparedTeam(teamA.team()), toComparedTeam(teamB.team()), lines, summary, verdict);
	}

	private static ComparedTeam toComparedTeam(Team team) {
		return new ComparedTeam(team.id(), team.name(), team.code());
	}

}
