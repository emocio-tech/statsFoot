package stats_foot.stats_foot.player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads {@code players.json} and {@code teams.json} once at construction and keeps the players in memory.
 * A read error is remembered and rethrown on each use, never at construction.
 */
@Repository
@ConditionalOnProperty(name = "statsfoot.player-source", havingValue = "json", matchIfMissing = true)
public class JsonPlayerRepository implements PlayerRepository {

	private final List<Player> players;
	private final Map<String, Player> playersById;
	private final PlayerDataUnavailableException error;

	public JsonPlayerRepository(@Value("${statsfoot.data-dir:data}") String dataDir) {
		List<Player> loaded = null;
		PlayerDataUnavailableException failure = null;
		try {
			loaded = load(Paths.get(dataDir));
		} catch (IOException | RuntimeException e) {
			failure = new PlayerDataUnavailableException(PlayerDataUnavailableException.UNAVAILABLE_MESSAGE, e);
		}
		this.players = loaded;
		this.playersById = loaded == null ? null
				: loaded.stream().collect(Collectors.toUnmodifiableMap(Player::id, Function.identity(), (a, b) -> a));
		this.error = failure;
	}

	@Override
	public List<Player> findAll() {
		checkAvailable();
		return players;
	}

	@Override
	public Optional<Player> findById(String id) {
		checkAvailable();
		return Optional.ofNullable(id == null ? null : playersById.get(id));
	}

	private void checkAvailable() {
		if (error != null) {
			throw error;
		}
	}

	private static List<Player> load(Path dir) throws IOException {
		JsonMapper mapper = JsonMapper.builder()
				.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.build();
		List<TeamRow> teamRows = mapper.readValue(Files.readAllBytes(dir.resolve("teams.json")),
				new TypeReference<List<TeamRow>>() { });
		List<PlayerRow> playerRows = mapper.readValue(Files.readAllBytes(dir.resolve("players.json")),
				new TypeReference<List<PlayerRow>>() { });
		Map<String, Team> teams = teamRows.stream()
				.collect(Collectors.toMap(TeamRow::id, t -> new Team(t.id(), t.name(), t.fifaCode())));
		Set<String> seenIds = new HashSet<>();
		for (PlayerRow row : playerRows) {
			if (!teams.containsKey(row.teamId())) {
				throw new IllegalStateException("Player " + row.id() + " references unknown team " + row.teamId());
			}
			if (!seenIds.add(row.id())) {
				throw new IllegalStateException("Duplicate player id " + row.id());
			}
		}
		return playerRows.stream()
				.map(p -> new Player(p.id(), p.name(), teams.get(p.teamId()), p.shirtNumber(), p.position(),
						p.positionDetail(), p.birthDate(), p.club(), p.heightCm(), p.preferredFoot(), p.captain(),
						p.stats(), p.goalkeeperStats()))
				.toList();
	}

	private record TeamRow(String id, String name, String fifaCode) {
	}

	private record PlayerRow(String id, String name, String teamId, Integer shirtNumber, String position,
			String positionDetail, LocalDate birthDate, String club, Integer heightCm, String preferredFoot,
			Boolean captain, PlayerStats stats, GoalkeeperStats goalkeeperStats) {
	}

}
