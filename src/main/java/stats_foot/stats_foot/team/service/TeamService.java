package stats_foot.stats_foot.team.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import stats_foot.stats_foot.player.exception.InvalidSearchException;
import stats_foot.stats_foot.player.util.NameNormalizer;
import stats_foot.stats_foot.team.dto.TeamSearchResult;
import stats_foot.stats_foot.team.dto.TeamSummary;
import stats_foot.stats_foot.team.model.TeamWithStats;
import stats_foot.stats_foot.team.repository.TeamRepository;

/** Business logic of team search. */
@Service
public class TeamService {

	public static final String BLANK_QUERY_MESSAGE = "Please enter a team name.";

	private final TeamRepository teamRepository;

	public TeamService(TeamRepository teamRepository) {
		this.teamRepository = teamRepository;
	}

	/**
	 * Searches teams whose normalised name contains the normalised query, or whose FIFA code equals it,
	 * sorted alphabetically (then by id).
	 *
	 * @throws InvalidSearchException if the query is null or blank
	 */
	public TeamSearchResult search(String name) {
		String query = NameNormalizer.normalize(name);
		if (query.isEmpty()) {
			throw new InvalidSearchException(BLANK_QUERY_MESSAGE);
		}
		List<TeamSummary> teams = teamRepository.findAll().stream()
				.filter(t -> NameNormalizer.normalize(t.team().name()).contains(query)
						|| NameNormalizer.normalize(t.team().code()).equals(query))
				.sorted(Comparator.comparing((TeamWithStats t) -> NameNormalizer.normalize(t.team().name()))
						.thenComparing(t -> t.team().id()))
				.map(t -> new TeamSummary(t.team().id(), t.team().name(), t.team().code()))
				.toList();
		return new TeamSearchResult(teams, teams.size());
	}

}
