package stats_foot.stats_foot.team.service;

import java.util.List;

import org.springframework.stereotype.Service;
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

	public TeamComparisonService(TeamRepository teamRepository) {
		this.teamRepository = teamRepository;
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
