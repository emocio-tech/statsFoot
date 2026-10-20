package stats_foot.stats_foot.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import stats_foot.stats_foot.player.dto.PlayerSearchResult;
import stats_foot.stats_foot.player.dto.PlayerSummary;
import stats_foot.stats_foot.player.exception.InvalidSearchException;
import stats_foot.stats_foot.player.model.Player;
import stats_foot.stats_foot.player.repository.JsonPlayerRepository;
import stats_foot.stats_foot.player.service.PlayerService;
import stats_foot.stats_foot.player.util.NameNormalizer;

class PlayerServiceTest {

	private static final String BLANK_MESSAGE = "Please enter a player name.";

	/** Players read independently from the JSON files: id, name, teamId, position. */
	private record JsonRow(String id, String name, String teamId, String position) {
	}

	private static List<JsonRow> rows;
	private static PlayerService service;

	@BeforeAll
	static void setUp() throws IOException {
		JsonMapper mapper = JsonMapper.builder().build();
		rows = new ArrayList<>();
		for (JsonNode node : mapper.readTree(Files.readAllBytes(TestDataFiles.playersFile()))) {
			rows.add(new JsonRow(node.get("id").asString(), node.get("name").asString(),
					node.get("teamId").asString(), node.get("position").asString()));
		}
		Clock clock = Clock.fixed(Instant.parse("2026-10-20T10:00:00Z"), ZoneOffset.UTC);
		service = new PlayerService(new JsonPlayerRepository(TestDataFiles.dataDir().toString()), clock);
	}

	private static List<JsonRow> expectedMatches(String query) {
		String normalized = NameNormalizer.normalize(query);
		return rows.stream()
				.filter(r -> NameNormalizer.normalize(r.name()).contains(normalized))
				.sorted(Comparator.comparing((JsonRow r) -> NameNormalizer.normalize(r.name())).thenComparing(JsonRow::id))
				.toList();
	}

	private static List<String> ids(PlayerSearchResult result) {
		return result.players().stream().map(PlayerSummary::id).toList();
	}

	private static List<String> names(PlayerSearchResult result) {
		return result.players().stream().map(PlayerSummary::name).toList();
	}

	private static JsonRow rowByName(String name) {
		return rows.stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
	}

	@ParameterizedTest
	@ValueSource(strings = { "suz", "SUZUKI", "sámi" })
	void findsSamiSuzukiWithPartialCaseAndAccentInsensitiveQuery(String query) {
		PlayerSearchResult result = service.search(query);
		assertThat(names(result)).contains("Sami Suzuki");
		assertThat(ids(result)).isEqualTo(expectedMatches(query).stream().map(JsonRow::id).toList());
	}

	@Test
	void surroundingAndRepeatedSpacesAreIgnored() {
		PlayerSearchResult spaced = service.search("  sami   suzuki  ");
		PlayerSearchResult clean = service.search("sami suzuki");
		assertThat(spaced).isEqualTo(clean);
		assertThat(spaced.total()).isEqualTo(1);
	}

	@Test
	void exactNameGivesSingleMatchWithSummaryFromData() {
		PlayerSearchResult result = service.search("sami suzuki");
		assertThat(result.total()).isEqualTo(1);
		assertThat(result.truncated()).isFalse();
		JsonRow expected = rowByName("Sami Suzuki");
		PlayerSummary summary = result.players().get(0);
		assertThat(summary.id()).isEqualTo(expected.id());
		assertThat(summary.name()).isEqualTo("Sami Suzuki");
		assertThat(summary.position()).isEqualTo(expected.position());
		Player player = new JsonPlayerRepository(TestDataFiles.dataDir().toString()).findById(expected.id()).orElseThrow();
		assertThat(summary.teamCode()).isEqualTo(player.team().code());
		assertThat(summary.teamName()).isEqualTo(player.team().name());
	}

	@Test
	void suzGivesSixteenSortedAlphabetically() {
		PlayerSearchResult result = service.search("suz");
		assertThat(result.total()).isEqualTo(16);
		assertThat(result.players()).hasSize(16);
		assertThat(result.truncated()).isFalse();
		assertThat(ids(result)).isEqualTo(expectedMatches("suz").stream().map(JsonRow::id).toList());
		List<String> normalized = names(result).stream().map(NameNormalizer::normalize).toList();
		assertThat(normalized).isSorted();
	}

	@Test
	void exactlyFiftyMatchesAreNotTruncated() {
		PlayerSearchResult result = service.search("ou");
		assertThat(result.players()).hasSize(50);
		assertThat(result.total()).isEqualTo(50);
		assertThat(result.truncated()).isFalse();
		assertThat(result.total()).isEqualTo(expectedMatches("ou").size());
	}

	@Test
	void fiftyOneMatchesAreTruncatedToFifty() {
		PlayerSearchResult result = service.search("id");
		assertThat(result.players()).hasSize(50);
		assertThat(result.total()).isEqualTo(51);
		assertThat(result.truncated()).isTrue();
		assertThat(result.total()).isEqualTo(expectedMatches("id").size());
		assertThat(ids(result)).isEqualTo(expectedMatches("id").stream().limit(50).map(JsonRow::id).toList());
	}

	@Test
	void manyMatchesReturnFirstFiftyInAlphabeticalOrder() {
		PlayerSearchResult result = service.search("a");
		List<JsonRow> expected = expectedMatches("a");
		assertThat(result.players()).hasSize(50);
		assertThat(result.total()).isEqualTo(1130);
		assertThat(result.total()).isEqualTo(expected.size());
		assertThat(result.truncated()).isTrue();
		assertThat(ids(result)).isEqualTo(expected.stream().limit(50).map(JsonRow::id).toList());
		assertThat(names(result)).isEqualTo(expected.stream().limit(50).map(JsonRow::name).toList());
	}

	@Test
	void noMatchGivesEmptyResultWithoutException() {
		PlayerSearchResult result = service.search("xyzxyz");
		assertThat(result.players()).isEmpty();
		assertThat(result.total()).isZero();
		assertThat(result.truncated()).isFalse();
	}

	@Test
	void accentedNameIsFoundWithoutAccent() {
		PlayerSearchResult result = service.search("ramirez");
		assertThat(ids(result)).contains("P-0004");
		assertThat(ids(result)).isEqualTo(expectedMatches("ramirez").stream().map(JsonRow::id).toList());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   ", "\t", " " })
	void blankQueryIsRejected(String query) {
		assertThatThrownBy(() -> service.search(query))
				.isInstanceOf(InvalidSearchException.class)
				.hasMessage(BLANK_MESSAGE);
	}

}
