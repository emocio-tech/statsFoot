package stats_foot.stats_foot.player.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import stats_foot.stats_foot.player.dto.PlayerProfile;
import stats_foot.stats_foot.player.dto.PlayerProfile.PlayerProfileStats;
import stats_foot.stats_foot.player.dto.PlayerSearchResult;
import stats_foot.stats_foot.player.dto.PlayerSummary;
import stats_foot.stats_foot.player.exception.InvalidSearchException;
import stats_foot.stats_foot.player.exception.PlayerNotFoundException;
import stats_foot.stats_foot.player.model.Player;
import stats_foot.stats_foot.player.model.PlayerStats;
import stats_foot.stats_foot.player.repository.PlayerRepository;
import stats_foot.stats_foot.player.util.NameNormalizer;

/** Business logic of the player feature. */
@Service
public class PlayerService {

	static final int MAX_RESULTS = 50;
	public static final String BLANK_QUERY_MESSAGE = "Please enter a player name.";
	public static final String NOT_FOUND_MESSAGE = "Player not found.";

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
