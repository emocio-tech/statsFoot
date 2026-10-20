package stats_foot.stats_foot.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import stats_foot.stats_foot.player.exception.PlayerDataUnavailableException;
import stats_foot.stats_foot.player.model.Player;
import stats_foot.stats_foot.player.model.PlayerStats;
import stats_foot.stats_foot.player.repository.JsonPlayerRepository;

class JsonPlayerRepositoryTest {

	private static final String UNAVAILABLE = "Player data is currently unavailable.";

	@TempDir
	Path tempDir;

	private static JsonPlayerRepository realRepository() {
		return new JsonPlayerRepository(TestDataFiles.dataDir().toString());
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

	private static JsonNode jsonPlayer(String id) throws IOException {
		return findNode(TestDataFiles.playersFile(), id);
	}

	private static JsonNode jsonTeam(String id) throws IOException {
		return findNode(TestDataFiles.teamsFile(), id);
	}

	private static Integer intOrNull(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? null : value.asInt();
	}

	private static void assertEqualsJson(Player player, JsonNode json, JsonNode team) {
		assertThat(player.id()).isEqualTo(json.get("id").asString());
		assertThat(player.name()).isEqualTo(json.get("name").asString());
		assertThat(player.team().id()).isEqualTo(json.get("teamId").asString());
		assertThat(player.team().name()).isEqualTo(team.get("name").asString());
		assertThat(player.team().code()).isEqualTo(team.get("fifaCode").asString());
		assertThat(player.shirtNumber()).isEqualTo(intOrNull(json, "shirtNumber"));
		assertThat(player.position()).isEqualTo(json.get("position").asString());
		assertThat(player.positionDetail()).isEqualTo(json.get("positionDetail").asString());
		assertThat(player.birthDate()).isEqualTo(LocalDate.parse(json.get("birthDate").asString()));
		assertThat(player.club()).isEqualTo(json.get("club").asString());
		assertThat(player.heightCm()).isEqualTo(intOrNull(json, "heightCm"));
		assertThat(player.preferredFoot()).isEqualTo(json.get("preferredFoot").asString());
		assertThat(player.captain()).isEqualTo(json.get("captain").asBoolean());
		JsonNode stats = json.get("stats");
		PlayerStats s = player.stats();
		assertThat(s.appearances()).isEqualTo(intOrNull(stats, "appearances"));
		assertThat(s.starts()).isEqualTo(intOrNull(stats, "starts"));
		assertThat(s.minutesPlayed()).isEqualTo(intOrNull(stats, "minutesPlayed"));
		assertThat(s.goals()).isEqualTo(intOrNull(stats, "goals"));
		assertThat(s.assists()).isEqualTo(intOrNull(stats, "assists"));
		assertThat(s.ownGoals()).isEqualTo(intOrNull(stats, "ownGoals"));
		assertThat(s.shots()).isEqualTo(intOrNull(stats, "shots"));
		assertThat(s.shotsOnTarget()).isEqualTo(intOrNull(stats, "shotsOnTarget"));
		assertThat(s.yellowCards()).isEqualTo(intOrNull(stats, "yellowCards"));
		assertThat(s.redCards()).isEqualTo(intOrNull(stats, "redCards"));
		assertThat(s.foulsCommitted()).isEqualTo(intOrNull(stats, "foulsCommitted"));
		assertThat(s.foulsSuffered()).isEqualTo(intOrNull(stats, "foulsSuffered"));
		assertThat(s.passes()).isEqualTo(intOrNull(stats, "passes"));
		JsonNode accuracy = stats.get("passAccuracy");
		if (accuracy == null || accuracy.isNull()) {
			assertThat(s.passAccuracy()).isNull();
		} else {
			assertThat(s.passAccuracy()).isEqualTo(accuracy.asDouble());
		}
		JsonNode gk = json.get("goalkeeperStats");
		if (gk == null || gk.isNull()) {
			assertThat(player.goalkeeperStats()).isNull();
		} else {
			assertThat(player.goalkeeperStats().saves()).isEqualTo(intOrNull(gk, "saves"));
			assertThat(player.goalkeeperStats().goalsConceded()).isEqualTo(intOrNull(gk, "goalsConceded"));
			assertThat(player.goalkeeperStats().cleanSheets()).isEqualTo(intOrNull(gk, "cleanSheets"));
			assertThat(player.goalkeeperStats().penaltiesSaved()).isEqualTo(intOrNull(gk, "penaltiesSaved"));
		}
	}

	private Path copyRealFiles() throws IOException {
		Files.copy(TestDataFiles.playersFile(), tempDir.resolve("players.json"), StandardCopyOption.REPLACE_EXISTING);
		Files.copy(TestDataFiles.teamsFile(), tempDir.resolve("teams.json"), StandardCopyOption.REPLACE_EXISTING);
		return tempDir;
	}

	private static void assertUnavailable(JsonPlayerRepository repository) {
		assertThatThrownBy(repository::findAll)
				.isInstanceOf(PlayerDataUnavailableException.class)
				.hasMessage(UNAVAILABLE)
				.hasCauseInstanceOf(Exception.class);
		assertThatThrownBy(() -> repository.findById("P-0001"))
				.isInstanceOf(PlayerDataUnavailableException.class)
				.hasMessage(UNAVAILABLE);
	}

	@Test
	void valuesEqualIndependentJsonParseForGoalkeeper() throws IOException {
		JsonNode json = jsonPlayer("P-0001");
		Player player = realRepository().findById("P-0001").orElseThrow();
		assertEqualsJson(player, json, jsonTeam(json.get("teamId").asString()));
	}

	@Test
	void valuesEqualIndependentJsonParseForPlayerWithPassAccuracy() throws IOException {
		JsonNode json = jsonPlayer("P-0003");
		Player player = realRepository().findById("P-0003").orElseThrow();
		assertEqualsJson(player, json, jsonTeam(json.get("teamId").asString()));
	}

	@Test
	void loadsAllPlayers() {
		assertThat(realRepository().findAll()).hasSize(1248);
	}

	@Test
	void passAccuracyIsNullForP0001AndNotNullForP0003() {
		JsonPlayerRepository repository = realRepository();
		assertThat(repository.findById("P-0001").orElseThrow().stats().passAccuracy()).isNull();
		assertThat(repository.findById("P-0003").orElseThrow().stats().passAccuracy()).isNotNull();
	}

	@Test
	void zeroStatisticStaysZero() {
		PlayerStats stats = realRepository().findById("P-0001").orElseThrow().stats();
		assertThat(stats.goals()).isEqualTo(0);
		assertThat(stats.appearances()).isEqualTo(0);
	}

	@Test
	void goalkeeperStatsPresentForGoalkeeperAndAbsentForForward() {
		JsonPlayerRepository repository = realRepository();
		assertThat(repository.findById("P-0001").orElseThrow().goalkeeperStats()).isNotNull();
		assertThat(repository.findById("P-0020").orElseThrow().goalkeeperStats()).isNull();
	}

	@Test
	void unknownIdGivesEmpty() {
		assertThat(realRepository().findById("P-9999")).isEmpty();
	}

	@Test
	void readsFromConfiguredFolder() throws IOException {
		JsonPlayerRepository repository = new JsonPlayerRepository(copyRealFiles().toString());
		assertThat(repository.findAll()).hasSize(1248);
		JsonNode json = jsonPlayer("P-0003");
		assertEqualsJson(repository.findById("P-0003").orElseThrow(), json, jsonTeam(json.get("teamId").asString()));
	}

	@Test
	void emptyFolderFailsOnUseNotOnConstruction() {
		JsonPlayerRepository repository = new JsonPlayerRepository(tempDir.toString());
		assertUnavailable(repository);
	}

	@Test
	void missingFolderFailsOnUseNotOnConstruction() {
		JsonPlayerRepository repository = new JsonPlayerRepository(tempDir.resolve("absent").toString());
		assertUnavailable(repository);
	}

	@Test
	void missingPlayersFileFailsOnUse() throws IOException {
		copyRealFiles();
		Files.delete(tempDir.resolve("players.json"));
		assertUnavailable(new JsonPlayerRepository(tempDir.toString()));
	}

	@Test
	void missingTeamsFileFailsOnUse() throws IOException {
		copyRealFiles();
		Files.delete(tempDir.resolve("teams.json"));
		assertUnavailable(new JsonPlayerRepository(tempDir.toString()));
	}

	@Test
	void invalidPlayersJsonFailsOnUse() throws IOException {
		copyRealFiles();
		Files.writeString(tempDir.resolve("players.json"), "{not json");
		assertUnavailable(new JsonPlayerRepository(tempDir.toString()));
	}

	@Test
	void unknownTeamIdFailsOnUseNotOnConstruction() throws IOException {
		copyRealFiles();
		String teamId = jsonPlayer("P-0001").get("teamId").asString();
		JsonMapper mapper = JsonMapper.builder().build();
		ArrayNode teams = (ArrayNode) mapper.readTree(Files.readAllBytes(tempDir.resolve("teams.json")));
		for (int i = teams.size() - 1; i >= 0; i--) {
			if (teamId.equals(teams.get(i).get("id").asString())) {
				teams.remove(i);
			}
		}
		Files.writeString(tempDir.resolve("teams.json"), mapper.writeValueAsString(teams));
		JsonPlayerRepository repository = new JsonPlayerRepository(tempDir.toString());
		assertUnavailable(repository);
		assertThat(catchThrowable(repository::findAll).getMessage()).doesNotContain(teamId).doesNotContain("P-0001");
	}

	@Test
	void duplicatePlayerIdFailsOnUseNotOnConstruction() throws IOException {
		copyRealFiles();
		JsonMapper mapper = JsonMapper.builder().build();
		ArrayNode players = (ArrayNode) mapper.readTree(Files.readAllBytes(tempDir.resolve("players.json")));
		players.add(players.get(0));
		Files.writeString(tempDir.resolve("players.json"), mapper.writeValueAsString(players));
		JsonPlayerRepository repository = new JsonPlayerRepository(tempDir.toString());
		assertUnavailable(repository);
		assertThat(catchThrowable(repository::findAll).getMessage()).doesNotContain("P-0001");
	}

	@Test
	void messageDoesNotExposeFilePath() {
		JsonPlayerRepository repository = new JsonPlayerRepository(tempDir.toString());
		Throwable error = catchThrowable(repository::findAll);
		assertThat(error.getMessage()).doesNotContain(tempDir.toString()).doesNotContain("json");
	}

	@Test
	void sameErrorOnEveryCall() {
		JsonPlayerRepository repository = new JsonPlayerRepository(tempDir.toString());
		Throwable first = catchThrowable(repository::findAll);
		Throwable second = catchThrowable(repository::findAll);
		Throwable third = catchThrowable(() -> repository.findById("P-0001"));
		assertThat(first).isInstanceOf(PlayerDataUnavailableException.class);
		assertThat(second).isSameAs(first);
		assertThat(third).isSameAs(first);
	}

	@Test
	void emptyFolderDoesNotFallBackToDefaultDataFolder() {
		// the real data folder exists (TestDataFiles checks it), yet an empty configured folder must not use it
		TestDataFiles.dataDir();
		assertThatThrownBy(() -> new JsonPlayerRepository(tempDir.toString()).findAll())
				.isInstanceOf(PlayerDataUnavailableException.class);
	}

	@Test
	void fileModifiedAfterConstructionIsNotReRead() throws IOException {
		copyRealFiles();
		JsonPlayerRepository repository = new JsonPlayerRepository(tempDir.toString());
		Files.writeString(tempDir.resolve("players.json"), "[]");
		assertThat(repository.findAll()).hasSize(1248);
		assertThat(repository.findById("P-0001")).isPresent();
	}

}
