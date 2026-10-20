package stats_foot.stats_foot.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import stats_foot.stats_foot.TestDataFile;
import stats_foot.stats_foot.TestDataFile.FilePlayer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** HTTP contract of the player API on the real application (in-memory database imported from the real file). */
@SpringBootTest
@AutoConfigureMockMvc
class PlayerControllerTest {

	private static final String BLANK_BODY = "{\"error\":\"Please enter a player name.\"}";
	private static final String NOT_FOUND_BODY = "{\"error\":\"Player not found.\"}";

	private static final List<String> REMOVED_FIELDS = List.of("shirtNumber", "position", "positionDetail",
			"birthDate", "age", "club", "heightCm", "preferredFoot", "captain", "goalkeeperStats", "passAccuracy");

	/** Contract field (camelCase) to field of the "tournament" block of the file. */
	private static final Map<String, String> STAT_FIELDS = new LinkedHashMap<>();

	static {
		STAT_FIELDS.put("matchesPlayed", "matches_played");
		STAT_FIELDS.put("matchesStarted", "matches_started");
		STAT_FIELDS.put("minutesPlayed", "minutes_played");
		STAT_FIELDS.put("goals", "goals");
		STAT_FIELDS.put("penaltyGoals", "penalty_goals");
		STAT_FIELDS.put("ownGoals", "own_goals");
		STAT_FIELDS.put("yellowCards", "yellow_cards");
		STAT_FIELDS.put("redCards", "red_cards");
		STAT_FIELDS.put("foulsCommitted", "fouls_committed");
		STAT_FIELDS.put("freeKicksWon", "free_kicks_won");
		STAT_FIELDS.put("freeKicksConceded", "free_kicks_conceded");
		STAT_FIELDS.put("duelsWon", "duels_won");
		STAT_FIELDS.put("duelsLost", "duels_lost");
		STAT_FIELDS.put("corners", "corners");
		STAT_FIELDS.put("offsides", "offsides");
		STAT_FIELDS.put("distanceKm", "distance_km");
		STAT_FIELDS.put("manOfTheMatchCount", "man_of_the_match_count");
		STAT_FIELDS.put("rating", "rating");
		STAT_FIELDS.put("ratingWeighted", "rating_weighted");
	}

	@Autowired
	private MockMvc mockMvc;

	/** The expected profile JSON, built from the file with the field names of the contract. */
	private static String expectedProfileJson(int id) {
		FilePlayer player = TestDataFile.player(id);
		Map<String, Object> stats = new LinkedHashMap<>();
		STAT_FIELDS.forEach((field, column) -> stats.put(field, player.tournament().get(column)));
		Map<String, Object> profile = new LinkedHashMap<>();
		profile.put("id", player.id());
		profile.put("name", player.name());
		profile.put("teamCode", player.teamCode());
		profile.put("teamName", player.teamName());
		profile.put("goalkeeperMatches", player.node().get("goalkeeperMatches"));
		profile.put("captainMatches", player.node().get("captainMatches"));
		profile.put("stats", stats);
		return JsonMapper.builder().build().writeValueAsString(profile);
	}

	@Test
	void rangelSearchHasNoPosition() throws Exception {
		mockMvc.perform(get("/api/players").param("name", "rangel"))
				.andExpect(status().isOk())
				.andExpect(content().json(
						"{\"total\":1,\"truncated\":false,\"players\":[{\"id\":1,\"name\":\"Raul RANGEL\","
								+ "\"teamCode\":\"MEX\",\"teamName\":\"Mexico\"}]}",
						true));
	}

	@Test
	void searchResultsHaveNoPositionKey() throws Exception {
		String body = mockMvc.perform(get("/api/players").param("name", "so"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("position");
	}

	@Test
	void soIsNotTruncated() throws Exception {
		mockMvc.perform(get("/api/players").param("name", "so"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(50))
				.andExpect(jsonPath("$.truncated").value(false))
				.andExpect(jsonPath("$.players.length()").value(50));
	}

	@Test
	void teIsTruncated() throws Exception {
		mockMvc.perform(get("/api/players").param("name", "te"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(51))
				.andExpect(jsonPath("$.truncated").value(true))
				.andExpect(jsonPath("$.players.length()").value(50));
	}

	@Test
	void manyMatchesAreCappedAtFifty() throws Exception {
		mockMvc.perform(get("/api/players").param("name", "a"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(913))
				.andExpect(jsonPath("$.truncated").value(true))
				.andExpect(jsonPath("$.players.length()").value(50));
	}

	@Test
	void noMatchGivesEmptyList() throws Exception {
		mockMvc.perform(get("/api/players").param("name", "xyzxyz"))
				.andExpect(status().isOk())
				.andExpect(content().json("{\"total\":0,\"truncated\":false,\"players\":[]}", true));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "  " })
	void blankNameIsRejected(String name) throws Exception {
		mockMvc.perform(get("/api/players").param("name", name))
				.andExpect(status().isBadRequest())
				.andExpect(content().json(BLANK_BODY, true));
	}

	@Test
	void missingNameIsRejected() throws Exception {
		mockMvc.perform(get("/api/players"))
				.andExpect(status().isBadRequest())
				.andExpect(content().json(BLANK_BODY, true));
	}

	@Test
	void profileOfRangelIsTheExactContract() throws Exception {
		mockMvc.perform(get("/api/players/1"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("application/json"))
				.andExpect(content().json(expectedProfileJson(1), true))
				.andExpect(jsonPath("$.goalkeeperMatches").value(5))
				.andExpect(jsonPath("$.stats.goals").value(0));
	}

	@Test
	void profileHasNoRemovedField() throws Exception {
		String body = mockMvc.perform(get("/api/players/1"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		JsonNode json = JsonMapper.builder().build().readTree(body);
		for (String field : REMOVED_FIELDS) {
			assertThat(json.has(field)).as(field).isFalse();
			assertThat(json.get("stats").has(field)).as("stats." + field).isFalse();
			assertThat(body).doesNotContain("\"" + field + "\"");
		}
	}

	@Test
	void nullIsKeptInTheJson() throws Exception {
		String body = mockMvc.perform(get("/api/players/316"))
				.andExpect(status().isOk())
				.andExpect(content().json(expectedProfileJson(316), true))
				.andExpect(jsonPath("$.stats.minutesPlayed").value(0))
				.andReturn().getResponse().getContentAsString();
		assertThat(body).contains("\"ratingWeighted\":null");
	}

	@Test
	void captainMatchesAreReported() throws Exception {
		mockMvc.perform(get("/api/players/2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.captainMatches").value(3))
				.andExpect(jsonPath("$.goalkeeperMatches").value(0));
	}

	@ParameterizedTest
	@ValueSource(strings = { "999999", "abc", "P-0001" })
	void unknownOrNonNumericIdIsNotFound(String id) throws Exception {
		mockMvc.perform(get("/api/players/{id}", id))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith("application/json"))
				.andExpect(content().json(NOT_FOUND_BODY, true));
	}

}
