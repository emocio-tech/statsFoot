package stats_foot.stats_foot.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PlayerControllerTest.FixedClockConfig.class)
class PlayerControllerTest {

	@TestConfiguration
	static class FixedClockConfig {

		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(Instant.parse("2026-10-20T10:00:00Z"), ZoneOffset.UTC);
		}

	}

	private static final String BLANK_MESSAGE = "Please enter a player name.";

	private static Map<String, JsonNode> jsonById;

	@Autowired
	private MockMvc mockMvc;

	@DynamicPropertySource
	static void dataDir(DynamicPropertyRegistry registry) {
		registry.add("statsfoot.data-dir", () -> TestDataFiles.dataDir().toString());
	}

	@BeforeAll
	static void readJson() throws IOException {
		JsonMapper mapper = JsonMapper.builder().build();
		jsonById = new HashMap<>();
		for (JsonNode node : mapper.readTree(Files.readAllBytes(TestDataFiles.playersFile()))) {
			jsonById.put(node.get("id").asString(), node);
		}
	}

	@Test
	void exactNameGivesOneSummary() throws Exception {
		JsonNode json = jsonById.get("P-0001");
		mockMvc.perform(get("/api/players").param("name", "sami suzuki"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.truncated").value(false))
				.andExpect(jsonPath("$.players.length()").value(1))
				.andExpect(jsonPath("$.players[0].id").value("P-0001"))
				.andExpect(jsonPath("$.players[0].name").value("Sami Suzuki"))
				.andExpect(jsonPath("$.players[0].teamCode").value(json.get("teamId").asString()))
				.andExpect(jsonPath("$.players[0].teamName").isNotEmpty())
				.andExpect(jsonPath("$.players[0].position").value(json.get("position").asString()));
	}

	@Test
	void suzGivesSixteen() throws Exception {
		mockMvc.perform(get("/api/players").param("name", "suz"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(16))
				.andExpect(jsonPath("$.truncated").value(false))
				.andExpect(jsonPath("$.players.length()").value(16));
	}

	@Test
	void exactlyFiftyIsNotTruncated() throws Exception {
		mockMvc.perform(get("/api/players").param("name", "ou"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(50))
				.andExpect(jsonPath("$.truncated").value(false))
				.andExpect(jsonPath("$.players.length()").value(50));
	}

	@Test
	void fiftyOneIsTruncated() throws Exception {
		mockMvc.perform(get("/api/players").param("name", "id"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(51))
				.andExpect(jsonPath("$.truncated").value(true))
				.andExpect(jsonPath("$.players.length()").value(50));
	}

	@Test
	void manyMatchesAreCappedAtFifty() throws Exception {
		mockMvc.perform(get("/api/players").param("name", "a"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(1130))
				.andExpect(jsonPath("$.truncated").value(true))
				.andExpect(jsonPath("$.players.length()").value(50));
	}

	@Test
	void noMatchGivesEmptyList() throws Exception {
		mockMvc.perform(get("/api/players").param("name", "xyzxyz"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(0))
				.andExpect(jsonPath("$.truncated").value(false))
				.andExpect(jsonPath("$.players.length()").value(0));
	}

	@Test
	void accentedQueryFindsSamiSuzuki() throws Exception {
		mockMvc.perform(get("/api/players?name={name}", "sámi"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.players[?(@.name == 'Sami Suzuki')]").isNotEmpty());
	}

	@Test
	void emptyNameIsRejected() throws Exception {
		mockMvc.perform(get("/api/players?name="))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(BLANK_MESSAGE));
	}

	@Test
	void spacesOnlyNameIsRejected() throws Exception {
		mockMvc.perform(get("/api/players?name={name}", "  "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(BLANK_MESSAGE));
	}

	@Test
	void missingNameIsRejected() throws Exception {
		mockMvc.perform(get("/api/players"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(BLANK_MESSAGE));
	}

	@Test
	void goalkeeperProfileHasAgeNullPassAccuracyAndGoalkeeperStats() throws Exception {
		String body = mockMvc.perform(get("/api/players/P-0001"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("P-0001"))
				.andExpect(jsonPath("$.name").value("Sami Suzuki"))
				.andExpect(jsonPath("$.birthDate").value("2006-11-03"))
				.andExpect(jsonPath("$.age").value(19))
				.andExpect(jsonPath("$.stats.goals").value(0))
				.andExpect(jsonPath("$.goalkeeperStats.saves").value(0))
				.andReturn().getResponse().getContentAsString();
		assertThat(body).contains("\"passAccuracy\":null");
	}

	@Test
	void profileMatchesTheJson() throws Exception {
		JsonNode json = jsonById.get("P-0003");
		mockMvc.perform(get("/api/players/P-0003"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value(json.get("name").asString()))
				.andExpect(jsonPath("$.teamCode").value(json.get("teamId").asString()))
				.andExpect(jsonPath("$.shirtNumber").value(json.get("shirtNumber").asInt()))
				.andExpect(jsonPath("$.club").value(json.get("club").asString()))
				.andExpect(jsonPath("$.stats.passes").value(json.get("stats").get("passes").asInt()))
				.andExpect(jsonPath("$.stats.passAccuracy").value(json.get("stats").get("passAccuracy").asDouble()));
	}

	@Test
	void captainIsReported() throws Exception {
		mockMvc.perform(get("/api/players/P-0005"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.captain").value(true));
	}

	@Test
	void outfieldPlayerHasNoGoalkeeperStatsKey() throws Exception {
		String body = mockMvc.perform(get("/api/players/P-0020"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("P-0020"))
				.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("goalkeeperStats");
	}

	@Test
	void unknownIdIsNotFound() throws Exception {
		mockMvc.perform(get("/api/players/P-9999"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith("application/json"))
				.andExpect(jsonPath("$.error").value("Player not found."));
	}

}
