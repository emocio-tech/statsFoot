package stats_foot.stats_foot.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

/** The application starts and answers 503 on both endpoints when the player data cannot be read. */
class PlayerDataUnavailableIntegrationTest {

	private static final String EXPECTED_BODY = "{\"error\":\"Player data is currently unavailable.\"}";

	private static void assertUnavailableTwice(MockMvc mockMvc, Path dir) throws Exception {
		for (int i = 0; i < 2; i++) {
			assertUnavailable(mockMvc, dir, "/api/players?name=x");
			assertUnavailable(mockMvc, dir, "/api/players/P-0001");
		}
	}

	private static void assertUnavailable(MockMvc mockMvc, Path dir, String url) throws Exception {
		String body = mockMvc.perform(get(url))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().json(EXPECTED_BODY, true))
				.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain(dir.toString()).doesNotContain("players.json").doesNotContain("\\").doesNotContain("/");
	}

	@Nested
	@SpringBootTest
	@AutoConfigureMockMvc
	class EmptyFolder {

		@TempDir
		static Path tempDir;

		@Autowired
		private MockMvc mockMvc;

		@DynamicPropertySource
		static void dataDir(DynamicPropertyRegistry registry) {
			registry.add("statsfoot.data-dir", () -> tempDir.toString());
		}

		@Test
		void answers503() throws Exception {
			assertUnavailableTwice(mockMvc, tempDir);
		}

	}

	@Nested
	@SpringBootTest
	@AutoConfigureMockMvc
	class MissingFolder {

		@TempDir
		static Path tempDir;

		@Autowired
		private MockMvc mockMvc;

		@DynamicPropertySource
		static void dataDir(DynamicPropertyRegistry registry) {
			registry.add("statsfoot.data-dir", () -> tempDir.resolve("does-not-exist").toString());
		}

		@Test
		void answers503() throws Exception {
			assertUnavailableTwice(mockMvc, tempDir);
		}

	}

	@Nested
	@SpringBootTest
	@AutoConfigureMockMvc
	class UnknownTeam {

		@TempDir
		static Path tempDir;

		@Autowired
		private MockMvc mockMvc;

		@DynamicPropertySource
		static void dataDir(DynamicPropertyRegistry registry) throws IOException {
			Files.copy(TestDataFiles.playersFile(), tempDir.resolve("players.json"));
			JsonMapper mapper = JsonMapper.builder().build();
			ArrayNode teams = (ArrayNode) mapper.readTree(Files.readAllBytes(TestDataFiles.teamsFile()));
			teams.remove(0);
			Files.writeString(tempDir.resolve("teams.json"), mapper.writeValueAsString(teams));
			registry.add("statsfoot.data-dir", () -> tempDir.toString());
		}

		@Test
		void answers503() throws Exception {
			assertUnavailableTwice(mockMvc, tempDir);
		}

	}

	@Nested
	@SpringBootTest
	@AutoConfigureMockMvc
	class NonJsonPlayersFile {

		@TempDir
		static Path tempDir;

		@Autowired
		private MockMvc mockMvc;

		@DynamicPropertySource
		static void dataDir(DynamicPropertyRegistry registry) throws IOException {
			Files.copy(TestDataFiles.teamsFile(), tempDir.resolve("teams.json"));
			Files.writeString(tempDir.resolve("players.json"), "{not json");
			registry.add("statsfoot.data-dir", () -> tempDir.toString());
		}

		@Test
		void answers503() throws Exception {
			assertUnavailableTwice(mockMvc, tempDir);
		}

	}

}
