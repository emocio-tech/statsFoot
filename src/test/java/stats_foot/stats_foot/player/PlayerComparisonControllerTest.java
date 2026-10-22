package stats_foot.stats_foot.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import stats_foot.stats_foot.TestDataFile;
import stats_foot.stats_foot.player.service.PlayerComparisonService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** HTTP contract of {@code GET /api/players/compare} on the real application (real imported data). */
@SpringBootTest
@AutoConfigureMockMvc
class PlayerComparisonControllerTest {

	private static final String URL = "/api/players/compare";

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PlayerComparisonService comparisonService;

	private String compareBody(String playerA, String playerB) throws Exception {
		return mockMvc.perform(get(URL).param("playerA", playerA).param("playerB", playerB))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	@Test
	void compare_validIds_returns200() throws Exception {
		mockMvc.perform(get(URL).param("playerA", "1").param("playerB", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.playerA.id").value(1))
				.andExpect(jsonPath("$.playerB.id").value(2))
				.andExpect(jsonPath("$.lines.length()").value(19));
	}

	@Test
	void compare_sameId_returns400() throws Exception {
		mockMvc.perform(get(URL).param("playerA", "1").param("playerB", "1"))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("{\"error\":\"The two players must be different.\"}", true));
	}

	@Test
	void compare_unknownA_returns404() throws Exception {
		mockMvc.perform(get(URL).param("playerA", "999999").param("playerB", "2"))
				.andExpect(status().isNotFound())
				.andExpect(content().json("{\"error\":\"Player A not found.\"}", true));
	}

	@Test
	void compare_unknownB_returns404() throws Exception {
		mockMvc.perform(get(URL).param("playerA", "1").param("playerB", "999999"))
				.andExpect(status().isNotFound())
				.andExpect(content().json("{\"error\":\"Player B not found.\"}", true));
	}

	@Test
	void compare_sameUnknownId_returns400() throws Exception {
		mockMvc.perform(get(URL).param("playerA", "999999").param("playerB", "999999"))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("{\"error\":\"The two players must be different.\"}", true));
	}

	@Test
	void compare_gkVsOutfield_roles() throws Exception {
		mockMvc.perform(get(URL).param("playerA", "1").param("playerB", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.playerA.role").value("GOALKEEPER"))
				.andExpect(jsonPath("$.playerA.teamCode").value("MEX"))
				.andExpect(jsonPath("$.playerB.role").value("OUTFIELD_PLAYER"))
				.andExpect(jsonPath("$.roleWarning").isNotEmpty());
	}

	@Test
	void compare_nullRating_player316() throws Exception {
		// Guard: the real file has no weighted rating for player 316.
		assertThat(TestDataFile.player(316).tournament().get("rating_weighted").isNull()).isTrue();
		JsonNode body = jsonMapper.readTree(compareBody("316", "2"));
		JsonNode line = null;
		for (JsonNode candidate : body.get("lines")) {
			if ("ratingWeighted".equals(candidate.get("key").asString())) {
				line = candidate;
			}
		}
		assertThat(line).isNotNull();
		assertThat(line.get("available").asBoolean()).isFalse();
		assertThat(line.get("difference") == null || line.get("difference").isNull()).isTrue();
		assertThat(line.get("leader") == null || line.get("leader").isNull()).isTrue();
	}

	@Test
	void compare_jsonShape() throws Exception {
		mockMvc.perform(get(URL).param("playerA", "1").param("playerB", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.playerA").exists())
				.andExpect(jsonPath("$.playerB").exists())
				.andExpect(jsonPath("$.lines[0].key").exists())
				.andExpect(jsonPath("$.summary.leadsA").exists())
				.andExpect(jsonPath("$.roleWarning").exists())
				.andExpect(jsonPath("$.verdict").isNotEmpty());
	}

	@Test
	void compare_isReadOnly() throws Exception {
		mockMvc.perform(post(URL).param("playerA", "1").param("playerB", "2"))
				.andExpect(status().isMethodNotAllowed());
		mockMvc.perform(put(URL).param("playerA", "1").param("playerB", "2"))
				.andExpect(status().isMethodNotAllowed());
	}

	@Test
	void service_sameReportAsHttp() throws Exception {
		JsonNode http = jsonMapper.readTree(compareBody("1", "2"));
		JsonNode direct = jsonMapper.readTree(jsonMapper.writeValueAsString(comparisonService.compare(1, 2)));
		assertThat(direct).isEqualTo(http);
	}

	@Test
	void errors_areEnglish() throws Exception {
		mockMvc.perform(get(URL))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("{\"error\":\"Both playerA and playerB are required.\"}", true));
		mockMvc.perform(get(URL).param("playerA", "1"))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("{\"error\":\"Both playerA and playerB are required.\"}", true));
		mockMvc.perform(get(URL).param("playerA", "abc").param("playerB", "2"))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("{\"error\":\"Player ids must be integers.\"}", true));
		mockMvc.perform(get(URL).param("playerA", "1").param("playerB", "1"))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("{\"error\":\"The two players must be different.\"}", true));
	}

	@Test
	void profileRoute_isStillRouted() throws Exception {
		mockMvc.perform(get("/api/players/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.stats").exists());
	}

}
