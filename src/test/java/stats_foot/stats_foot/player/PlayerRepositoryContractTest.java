package stats_foot.stats_foot.player;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import stats_foot.stats_foot.player.model.GoalkeeperStats;
import stats_foot.stats_foot.player.model.Player;
import stats_foot.stats_foot.player.model.PlayerStats;
import stats_foot.stats_foot.player.model.Team;
import stats_foot.stats_foot.player.repository.PlayerRepository;

/**
 * Contract every {@link PlayerRepository} adapter must satisfy. Expected values are read independently
 * from the real data files. Subclasses only provide the repository under test.
 */
abstract class PlayerRepositoryContractTest {

	/** Returns a repository loaded with the real players. */
	protected abstract PlayerRepository repository();

	@Test
	void findAllReturnsAllPlayers() {
		assertThat(repository().findAll()).hasSize(1248);
	}

	@ParameterizedTest
	@ValueSource(strings = { "P-0001", "P-0003", "P-0004", "P-0005", "P-0020" })
	void findByIdReturnsRecordEqualToIndependentRead(String id) throws IOException {
		Player expected = expectedPlayer(id);
		Player actual = repository().findById(id).orElseThrow();
		assertThat(actual).isEqualTo(expected);
		assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
	}

	@Test
	void findByIdReturnsSameRecordAsFindAll() {
		PlayerRepository repository = repository();
		Player fromAll = repository.findAll().stream().filter(p -> p.id().equals("P-0003")).findFirst().orElseThrow();
		assertThat(repository.findById("P-0003")).contains(fromAll);
	}

	@Test
	void unknownIdGivesEmpty() {
		assertThat(repository().findById("P-9999")).isEmpty();
	}

	@Test
	void passAccuracyNullIsPreserved() {
		assertThat(repository().findById("P-0001").orElseThrow().stats().passAccuracy()).isNull();
	}

	private static Player expectedPlayer(String id) throws IOException {
		JsonNode json = findNode(TestDataFiles.playersFile(), id);
		JsonNode team = findNode(TestDataFiles.teamsFile(), json.get("teamId").asString());
		JsonNode stats = json.get("stats");
		JsonNode gk = json.get("goalkeeperStats");
		return new Player(
				json.get("id").asString(),
				json.get("name").asString(),
				new Team(team.get("id").asString(), team.get("name").asString(), team.get("fifaCode").asString()),
				intOrNull(json, "shirtNumber"),
				json.get("position").asString(),
				json.get("positionDetail").asString(),
				LocalDate.parse(json.get("birthDate").asString()),
				json.get("club").asString(),
				intOrNull(json, "heightCm"),
				json.get("preferredFoot").asString(),
				json.get("captain").asBoolean(),
				new PlayerStats(
						intOrNull(stats, "appearances"), intOrNull(stats, "starts"), intOrNull(stats, "minutesPlayed"),
						intOrNull(stats, "goals"), intOrNull(stats, "assists"), intOrNull(stats, "ownGoals"),
						intOrNull(stats, "shots"), intOrNull(stats, "shotsOnTarget"), intOrNull(stats, "yellowCards"),
						intOrNull(stats, "redCards"), intOrNull(stats, "foulsCommitted"),
						intOrNull(stats, "foulsSuffered"), intOrNull(stats, "passes"), doubleOrNull(stats, "passAccuracy")),
				gk == null || gk.isNull() ? null
						: new GoalkeeperStats(intOrNull(gk, "saves"), intOrNull(gk, "goalsConceded"),
								intOrNull(gk, "cleanSheets"), intOrNull(gk, "penaltiesSaved")));
	}

	private static JsonNode findNode(Path file, String id) throws IOException {
		JsonMapper mapper = JsonMapper.builder().build();
		for (JsonNode node : mapper.readTree(Files.readAllBytes(file))) {
			if (id.equals(node.get("id").asString())) {
				return node;
			}
		}
		throw new IllegalStateException("no entry " + id);
	}

	private static Integer intOrNull(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? null : value.asInt();
	}

	private static Double doubleOrNull(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? null : value.asDouble();
	}

}
