package stats_foot.stats_foot.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import stats_foot.stats_foot.player.dto.PlayerProfile;
import stats_foot.stats_foot.player.exception.PlayerNotFoundException;
import stats_foot.stats_foot.player.model.GoalkeeperStats;
import stats_foot.stats_foot.player.model.PlayerStats;
import stats_foot.stats_foot.player.repository.JsonPlayerRepository;
import stats_foot.stats_foot.player.service.PlayerService;

class PlayerServiceProfileTest {

	private static final String[] STAT_KEYS = { "appearances", "starts", "minutesPlayed", "goals", "assists", "ownGoals",
			"shots", "shotsOnTarget", "yellowCards", "redCards", "foulsCommitted", "foulsSuffered", "passes",
			"passAccuracy" };

	private static Map<String, JsonNode> jsonById;
	private static Map<String, String> teamNames;
	private static JsonPlayerRepository repository;

	@BeforeAll
	static void setUp() throws IOException {
		JsonMapper mapper = JsonMapper.builder().build();
		jsonById = new HashMap<>();
		for (JsonNode node : mapper.readTree(Files.readAllBytes(TestDataFiles.playersFile()))) {
			jsonById.put(node.get("id").asString(), node);
		}
		teamNames = new HashMap<>();
		for (JsonNode node : mapper.readTree(Files.readAllBytes(TestDataFiles.teamsFile()))) {
			teamNames.put(node.get("id").asString(), node.get("name").asString());
		}
		repository = new JsonPlayerRepository(TestDataFiles.dataDir().toString());
	}

	private static PlayerService serviceAt(String date) {
		Clock clock = Clock.fixed(Instant.parse(date + "T12:00:00Z"), ZoneOffset.UTC);
		return new PlayerService(repository, clock);
	}

	private static Object statValue(PlayerStats stats, String key) {
		return switch (key) {
			case "appearances" -> stats.appearances();
			case "starts" -> stats.starts();
			case "minutesPlayed" -> stats.minutesPlayed();
			case "goals" -> stats.goals();
			case "assists" -> stats.assists();
			case "ownGoals" -> stats.ownGoals();
			case "shots" -> stats.shots();
			case "shotsOnTarget" -> stats.shotsOnTarget();
			case "yellowCards" -> stats.yellowCards();
			case "redCards" -> stats.redCards();
			case "foulsCommitted" -> stats.foulsCommitted();
			case "foulsSuffered" -> stats.foulsSuffered();
			case "passes" -> stats.passes();
			case "passAccuracy" -> stats.passAccuracy();
			default -> throw new IllegalArgumentException(key);
		};
	}

	@Test
	void ageIsNineteenBeforeBirthdayOfP0001() {
		assertThat(serviceAt("2026-10-20").getProfile("P-0001").age()).isEqualTo(19);
		assertThat(serviceAt("2026-11-02").getProfile("P-0001").age()).isEqualTo(19);
	}

	@Test
	void ageIsTwentyOnBirthdayOfP0001() {
		assertThat(serviceAt("2026-11-03").getProfile("P-0001").age()).isEqualTo(20);
	}

	@ParameterizedTest
	@ValueSource(strings = { "P-0003", "P-0005" })
	void identityAndStatsEqualTheJson(String id) {
		JsonNode json = jsonById.get(id);
		PlayerProfile profile = serviceAt("2026-10-20").getProfile(id);
		assertThat(profile.id()).isEqualTo(id);
		assertThat(profile.name()).isEqualTo(json.get("name").asString());
		assertThat(profile.teamCode()).isEqualTo(json.get("teamId").asString());
		assertThat(profile.teamName()).isEqualTo(teamNames.get(json.get("teamId").asString()));
		assertThat(profile.shirtNumber()).isEqualTo(json.get("shirtNumber").asInt());
		assertThat(profile.position()).isEqualTo(json.get("position").asString());
		assertThat(profile.positionDetail()).isEqualTo(json.get("positionDetail").asString());
		assertThat(profile.birthDate()).hasToString(json.get("birthDate").asString());
		assertThat(profile.club()).isEqualTo(json.get("club").asString());
		assertThat(profile.heightCm()).isEqualTo(json.get("heightCm").asInt());
		assertThat(profile.preferredFoot()).isEqualTo(json.get("preferredFoot").asString());
		assertThat(profile.captain()).isEqualTo(json.get("captain").asBoolean());
		JsonNode stats = json.get("stats");
		for (String key : STAT_KEYS) {
			Object expected = stats.get(key).isNull() ? null
					: key.equals("passAccuracy") ? (Object) stats.get(key).asDouble() : (Object) stats.get(key).asInt();
			assertThat(statValue(profile.stats(), key)).as(key).isEqualTo(expected);
		}
	}

	@Test
	void passAccuracyIsNullForP0001AndNotNullForP0003() {
		assertThat(serviceAt("2026-10-20").getProfile("P-0001").stats().passAccuracy()).isNull();
		assertThat(serviceAt("2026-10-20").getProfile("P-0003").stats().passAccuracy()).isNotNull();
	}

	@Test
	void zeroStatisticStaysZero() {
		PlayerStats stats = serviceAt("2026-10-20").getProfile("P-0001").stats();
		assertThat(stats.goals()).isEqualTo(0);
		assertThat(stats.passes()).isEqualTo(0);
	}

	@Test
	void goalkeeperStatsArePresentForGoalkeeperAndAbsentForOutfieldPlayer() {
		PlayerService service = serviceAt("2026-10-20");
		JsonNode expected = jsonById.get("P-0001").get("goalkeeperStats");
		GoalkeeperStats stats = service.getProfile("P-0001").goalkeeperStats();
		assertThat(stats).isNotNull();
		assertThat(stats.saves()).isEqualTo(expected.get("saves").asInt());
		assertThat(stats.goalsConceded()).isEqualTo(expected.get("goalsConceded").asInt());
		assertThat(stats.cleanSheets()).isEqualTo(expected.get("cleanSheets").asInt());
		assertThat(stats.penaltiesSaved()).isEqualTo(expected.get("penaltiesSaved").asInt());
		assertThat(service.getProfile("P-0020").goalkeeperStats()).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "P-9999", "", "   ", "\t" })
	void unknownOrBlankIdIsNotFound(String id) {
		assertThatThrownBy(() -> serviceAt("2026-10-20").getProfile(id))
				.isInstanceOf(PlayerNotFoundException.class)
				.hasMessage("Player not found.");
	}

}
