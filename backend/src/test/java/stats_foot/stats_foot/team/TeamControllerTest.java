package stats_foot.stats_foot.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import stats_foot.stats_foot.FullDataTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import stats_foot.stats_foot.team.service.TeamComparisonService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

/** HTTP contract of {@code /api/teams} on the real application (real imported data). */
@FullDataTest
@AutoConfigureMockMvc
class TeamControllerTest {

	private static final String SEARCH_URL = "/api/teams";
	private static final String COMPARE_URL = "/api/teams/compare";

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TeamComparisonService comparisonService;

	@Test
	void search_isCaseAndAccentInsensitive() throws Exception {
		mockMvc.perform(get(SEARCH_URL).param("name", "  ARGENT "))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.teams[0].id").value(37))
				.andExpect(jsonPath("$.teams[0].name").value("Argentina"));
	}

	@Test
	void search_byFifaCode() throws Exception {
		mockMvc.perform(get(SEARCH_URL).param("name", "alg"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.teams[0].code").value("ALG"));
	}

	@Test
	void search_noMatch_returnsEmptyList() throws Exception {
		mockMvc.perform(get(SEARCH_URL).param("name", "zzzz"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(0))
				.andExpect(jsonPath("$.teams.length()").value(0));
	}

	@Test
	void search_blankOrMissing_returns400WithTeamMessage() throws Exception {
		mockMvc.perform(get(SEARCH_URL).param("name", "  "))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("{\"error\":\"Please enter a team name.\"}", true));
		mockMvc.perform(get(SEARCH_URL))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("{\"error\":\"Please enter a team name.\"}", true));
	}

	@Test
	void compare_validIds_returnsTheReport() throws Exception {
		mockMvc.perform(get(COMPARE_URL).param("teamA", "37").param("teamB", "38"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.teamA.name").value("Argentina"))
				.andExpect(jsonPath("$.teamB.code").value("ALG"))
				.andExpect(jsonPath("$.lines.length()").value(19))
				.andExpect(jsonPath("$.summary.leadsA").exists())
				.andExpect(jsonPath("$.verdict").isNotEmpty());
	}

	@Test
	void compare_reportMatchesTheDataFile() throws Exception {
		JsonNode body = jsonMapper.readTree(mockMvc.perform(get(COMPARE_URL).param("teamA", "37").param("teamB", "38"))
				.andReturn().getResponse().getContentAsString());
		JsonNode points = null;
		for (JsonNode line : body.get("lines")) {
			if ("points".equals(line.get("key").asText())) {
				points = line;
			}
		}
		assertThat(points).isNotNull();
		assertThat(points.get("valueA").asInt()).isEqualTo(21);
		assertThat(points.get("valueB").asInt()).isEqualTo(4);
		assertThat(points.get("difference").asInt()).isEqualTo(17);
		assertThat(points.get("leader").asText()).isEqualTo("A");
	}

	@Test
	void compare_sameId_returns400() throws Exception {
		mockMvc.perform(get(COMPARE_URL).param("teamA", "37").param("teamB", "37"))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("{\"error\":\"The two teams must be different.\"}", true));
	}

	@Test
	void compare_unknownTeam_returns404() throws Exception {
		mockMvc.perform(get(COMPARE_URL).param("teamA", "999999").param("teamB", "38"))
				.andExpect(status().isNotFound())
				.andExpect(content().json("{\"error\":\"Team A not found.\"}", true));
		mockMvc.perform(get(COMPARE_URL).param("teamA", "37").param("teamB", "999999"))
				.andExpect(status().isNotFound())
				.andExpect(content().json("{\"error\":\"Team B not found.\"}", true));
	}

	@Test
	void compare_missingOrInvalidParameters_return400() throws Exception {
		mockMvc.perform(get(COMPARE_URL).param("teamA", "37"))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("{\"error\":\"Both teamA and teamB are required.\"}", true));
		mockMvc.perform(get(COMPARE_URL).param("teamA", "x").param("teamB", "38"))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("{\"error\":\"Team ids must be integers.\"}", true));
	}

	@Test
	void compare_isReadOnly() throws Exception {
		mockMvc.perform(post(COMPARE_URL).param("teamA", "37").param("teamB", "38"))
				.andExpect(status().isMethodNotAllowed());
	}

	@Test
	void service_sameReportAsHttp() throws Exception {
		JsonNode http = jsonMapper.readTree(mockMvc.perform(get(COMPARE_URL).param("teamA", "37").param("teamB", "38"))
				.andReturn().getResponse().getContentAsString());
		JsonNode direct = jsonMapper.readTree(jsonMapper.writeValueAsString(comparisonService.compare(37, 38)));
		assertThat(direct).isEqualTo(http);
	}

}
