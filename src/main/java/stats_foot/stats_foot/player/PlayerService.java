package stats_foot.stats_foot.player;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

/** Business logic of the player feature. */
@Service
public class PlayerService {

	static final int MAX_RESULTS = 50;
	static final String BLANK_QUERY_MESSAGE = "Please enter a player name.";
	static final String NOT_FOUND_MESSAGE = "Player not found.";

	private final PlayerRepository playerRepository;
	private final Clock clock;

	public PlayerService(PlayerRepository playerRepository, Clock clock) {
		this.playerRepository = playerRepository;
		this.clock = clock;
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
	 * Builds the profile of a player, with the age in full years computed on the date of the injected clock.
	 *
	 * @throws PlayerNotFoundException if the id is null, blank or unknown
	 */
	public PlayerProfile getProfile(String id) {
		if (id == null || id.isBlank()) {
			throw new PlayerNotFoundException(NOT_FOUND_MESSAGE);
		}
		Player player = playerRepository.findById(id).orElseThrow(() -> new PlayerNotFoundException(NOT_FOUND_MESSAGE));
		int age = Period.between(player.birthDate(), LocalDate.now(clock)).getYears();
		return new PlayerProfile(player.id(), player.name(), player.team().code(), player.team().name(),
				player.shirtNumber(), player.position(), player.positionDetail(), player.birthDate(), age,
				player.club(), player.heightCm(), player.preferredFoot(), player.captain(), player.stats(),
				player.goalkeeperStats());
	}

	private static PlayerSummary toSummary(Player player) {
		return new PlayerSummary(player.id(), player.name(), player.team().code(), player.team().name(),
				player.position());
	}

}
