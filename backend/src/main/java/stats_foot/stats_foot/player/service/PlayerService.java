package stats_foot.stats_foot.player.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import stats_foot.stats_foot.player.dto.PlayerProfile;
import stats_foot.stats_foot.player.dto.PlayerProfile.PlayerProfileStats;
import stats_foot.stats_foot.player.dto.PlayerQueryResult;
import stats_foot.stats_foot.player.dto.PlayerQueryResult.PlayerQueryMatch;
import stats_foot.stats_foot.player.dto.PlayerSearchResult;
import stats_foot.stats_foot.player.dto.PlayerSummary;
import stats_foot.stats_foot.player.exception.InvalidSearchException;
import stats_foot.stats_foot.player.exception.PlayerNotFoundException;
import stats_foot.stats_foot.player.model.ComparisonOperator;
import stats_foot.stats_foot.player.model.Player;
import stats_foot.stats_foot.player.model.PlayerStat;
import stats_foot.stats_foot.player.model.PlayerStats;
import stats_foot.stats_foot.player.repository.PlayerRepository;
import stats_foot.stats_foot.player.util.NameNormalizer;

/** Business logic of the player feature. */
@Service
public class PlayerService {

	static final int MAX_RESULTS = 50;
	public static final String BLANK_QUERY_MESSAGE = "Please enter a player name.";
	public static final String NOT_FOUND_MESSAGE = "Player not found.";
	public static final String INCOMPLETE_FILTER_MESSAGE = "A statistic filter needs an operator and a value.";
	public static final String INVALID_LIMIT_MESSAGE = "The limit must be at least 1.";
	static final int DEFAULT_QUERY_LIMIT = 10;

	private final PlayerRepository playerRepository;

	public PlayerService(PlayerRepository playerRepository) {
		this.playerRepository = playerRepository;
	}

	/**
	 * Searches players whose normalised name contains the normalised query, sorted alphabetically
	 * (then by id), capped at {@value #MAX_RESULTS}.
	 *
	 * @throws InvalidSearchException if the query is null or blank
	 */
	public PlayerSearchResult search(String name) {
		String query = NameNormalizer.normalize(name);
		if (query.isEmpty()) {
			throw new InvalidSearchException(BLANK_QUERY_MESSAGE);
		}
		List<Player> matches = playerRepository.findAll().stream()
				.filter(p -> NameNormalizer.normalize(p.name()).contains(query))
				.sorted(Comparator.comparing((Player p) -> NameNormalizer.normalize(p.name())).thenComparing(Player::id))
				.toList();
		List<PlayerSummary> players = matches.stream()
				.limit(MAX_RESULTS)
				.map(PlayerService::toSummary)
				.toList();
		return new PlayerSearchResult(players, matches.size(), matches.size() > MAX_RESULTS);
	}

	/**
	 * Builds the profile of a player.
	 *
	 * @throws PlayerNotFoundException if the id is unknown
	 */
	public PlayerProfile getProfile(int id) {
		Player player = playerRepository.findById(id).orElseThrow(() -> new PlayerNotFoundException(NOT_FOUND_MESSAGE));
		return new PlayerProfile(player.id(), player.name(), player.team().code(), player.team().name(),
				player.goalkeeperMatches(), player.captainMatches(), toProfileStats(player.stats()));
	}

	/**
	 * Queries players by team, captaincy and/or a statistic condition, optionally sorted by a statistic.
	 * Players whose filter or sort statistic is not available are left out. Ties keep the alphabetical order.
	 *
	 * @param team team name or FIFA code (accent and case-insensitive, exact match), or null for all teams
	 * @param captainsOnly true to keep only players who captained at least one match
	 * @param filterStat statistic to filter on, with {@code operator} and {@code value}; or null for no condition
	 * @param sortBy statistic to sort on, or null for alphabetical order
	 * @param limit maximum number of players returned, null for {@value #DEFAULT_QUERY_LIMIT}, capped at {@value #MAX_RESULTS}
	 * @throws InvalidSearchException if the filter is incomplete or the limit is not positive
	 */
	public PlayerQueryResult query(String team, Boolean captainsOnly, PlayerStat filterStat, ComparisonOperator operator,
			Double value, PlayerStat sortBy, boolean descending, Integer limit) {
		if (filterStat != null && (operator == null || value == null)) {
			throw new InvalidSearchException(INCOMPLETE_FILTER_MESSAGE);
		}
		if (limit != null && limit < 1) {
			throw new InvalidSearchException(INVALID_LIMIT_MESSAGE);
		}
		int max = Math.min(limit == null ? DEFAULT_QUERY_LIMIT : limit, MAX_RESULTS);
		String wantedTeam = NameNormalizer.normalize(team);

		Comparator<Player> byName = Comparator.comparing((Player p) -> NameNormalizer.normalize(p.name()))
				.thenComparing(Player::id);
		Comparator<Player> order = byName;
		if (sortBy != null) {
			Comparator<Player> byValue = Comparator.comparing(p -> sortBy.valueOf(p));
			order = (descending ? byValue.reversed() : byValue).thenComparing(byName);
		}

		List<Player> matches = playerRepository.findAll().stream()
				.filter(p -> wantedTeam.isEmpty() || wantedTeam.equals(NameNormalizer.normalize(p.team().name()))
						|| wantedTeam.equals(NameNormalizer.normalize(p.team().code())))
				.filter(p -> !Boolean.TRUE.equals(captainsOnly) || p.captainMatches() > 0)
				.filter(p -> filterStat == null || filterStat.valueOf(p) != null
						&& operator.test(filterStat.valueOf(p), value))
				.filter(p -> sortBy == null || sortBy.valueOf(p) != null)
				.sorted(order)
				.toList();
		List<PlayerQueryMatch> players = matches.stream()
				.limit(max)
				.map(p -> new PlayerQueryMatch(p.id(), p.name(), p.team().code(), p.team().name(), p.captainMatches(),
						sortBy == null ? null : sortBy.valueOf(p)))
				.toList();
		return new PlayerQueryResult(players, matches.size(), matches.size() > max);
	}

	private static PlayerSummary toSummary(Player player) {
		return new PlayerSummary(player.id(), player.name(), player.team().code(), player.team().name());
	}

	private static PlayerProfileStats toProfileStats(PlayerStats s) {
		return new PlayerProfileStats(s.matchesPlayed(), s.matchesStarted(), s.minutesPlayed(), s.goals(),
				s.penaltyGoals(), s.ownGoals(), s.yellowCards(), s.redCards(), s.foulsCommitted(), s.freeKicksWon(),
				s.freeKicksConceded(), s.duelsWon(), s.duelsLost(), s.corners(), s.offsides(), s.distanceKm(),
				s.manOfTheMatchCount(), s.rating(), s.ratingWeighted());
	}

}
