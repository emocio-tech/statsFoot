package stats_foot.stats_foot.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import stats_foot.stats_foot.TestDataFile;

/**
 * The application starts and answers 503 on both endpoints when the player data cannot be read: unreachable
 * database, missing data file, truncated data file. Each scenario uses its own in-memory database.
 */
class PlayerDataUnavailableIntegrationTest {

	private static final String EXPECTED_BODY = "{\"error\":\"Player data is currently unavailable.\"}";
	private static final String H2_OPTIONS = ";MODE=PostgreSQL;NON_KEYWORDS=MINUTE,VALUE;DB_CLOSE_DELAY=-1";

	private static String uniqueMemoryUrl(String prefix) {
		return "jdbc:h2:mem:" + prefix + UUID.randomUUID().toString().replace("-", "") + H2_OPTIONS;
	}

	private static void assertUnavailableTwice(MockMvc mockMvc, Path dir) throws Exception {
		for (int i = 0; i < 2; i++) {
			assertUnavailable(mockMvc, dir, "/api/players?name=x");
			assertUnavailable(mockMvc, dir, "/api/players/1");
		}
	}

	private static void assertUnavailable(MockMvc mockMvc, Path dir, String url) throws Exception {
		String body = mockMvc.perform(get(url))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().json(EXPECTED_BODY, true))
				.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain(dir.toString()).doesNotContain("players.json")
				.doesNotContain("teams.json").doesNotContain("donneesStats").doesNotContain("\\")
				.doesNotContain("/");
	}

	@Nested
	@SpringBootTest
	@AutoConfigureMockMvc
	class UnreachableDatabase {

		@TempDir
		static Path tempDir;

		@Autowired
		private MockMvc mockMvc;

		@DynamicPropertySource
		static void database(DynamicPropertyRegistry registry) throws IOException {
			// A regular file used as a directory: H2 cannot create the database there.
			Path plainFile = Files.writeString(tempDir.resolve("plain.txt"), "not a directory");
			String url = "jdbc:h2:file:" + plainFile.toString().replace('\\', '/') + "/db" + H2_OPTIONS;
			registry.add("spring.datasource.url", () -> url);
		}

		@Test
		void contextStartsAndAnswers503() throws Exception {
			assertUnavailableTwice(mockMvc, tempDir);
		}

	}

	@Nested
	@SpringBootTest
	@AutoConfigureMockMvc
	class MissingDataFile {

		@TempDir
		static Path tempDir;

		@Autowired
		private MockMvc mockMvc;

		@DynamicPropertySource
		static void database(DynamicPropertyRegistry registry) {
			String url = uniqueMemoryUrl("missingfile");
			registry.add("spring.datasource.url", () -> url);
			registry.add("statsfoot.data-file", () -> tempDir.resolve("does-not-exist.json").toString());
		}

		@Test
		void answers503() throws Exception {
			assertUnavailableTwice(mockMvc, tempDir);
		}

	}

	@Nested
	@SpringBootTest
	@AutoConfigureMockMvc
	class TruncatedDataFile {

		@TempDir
		static Path tempDir;

		@Autowired
		private MockMvc mockMvc;

		@Autowired
		private JdbcTemplate jdbcTemplate;

		@DynamicPropertySource
		static void database(DynamicPropertyRegistry registry) throws IOException {
			byte[] all = Files.readAllBytes(TestDataFile.PATH);
			Path truncated = Files.write(tempDir.resolve("donneesStats.json"),
					Arrays.copyOf(all, all.length / 2));
			String url = uniqueMemoryUrl("truncated");
			registry.add("spring.datasource.url", () -> url);
			registry.add("statsfoot.data-file", () -> truncated.toString());
		}

		@Test
		void answers503AndKeepsNoPartialData() throws Exception {
			assertUnavailableTwice(mockMvc, tempDir);
			assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player", Integer.class)).isZero();
			assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM team", Integer.class)).isZero();
			assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM match", Integer.class)).isZero();
		}

	}

	@Nested
	@SpringBootTest
	@AutoConfigureMockMvc
	class MissingDataFileOnComparison {

		@TempDir
		static Path tempDir;

		@Autowired
		private MockMvc mockMvc;

		@DynamicPropertySource
		static void database(DynamicPropertyRegistry registry) {
			String url = uniqueMemoryUrl("missingfilecompare");
			registry.add("spring.datasource.url", () -> url);
			registry.add("statsfoot.data-file", () -> tempDir.resolve("does-not-exist.json").toString());
		}

		@Test
		void compareAnswers503() throws Exception {
			for (int i = 0; i < 2; i++) {
				assertUnavailable(mockMvc, tempDir, "/api/players/compare?playerA=1&playerB=2");
			}
		}

	}

}
