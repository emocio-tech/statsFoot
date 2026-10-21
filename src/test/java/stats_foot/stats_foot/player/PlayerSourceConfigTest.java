package stats_foot.stats_foot.player;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import javax.sql.DataSource;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import jakarta.persistence.EntityManagerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import stats_foot.stats_foot.player.controller.PlayerController;
import stats_foot.stats_foot.player.repository.JpaPlayerRepository;
import stats_foot.stats_foot.player.repository.JsonPlayerRepository;
import stats_foot.stats_foot.player.repository.PlayerJpaRepository;
import stats_foot.stats_foot.player.repository.PlayerRepository;
import stats_foot.stats_foot.player.service.PlayerService;

/** Checks which player source is wired depending on the configuration. */
class PlayerSourceConfigTest {

	@Test
	void defaultPropertiesAreDataAndJson() throws IOException {
		Properties properties = new Properties();
		try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
			properties.load(in);
		}
		assertThat(properties.getProperty("statsfoot.data-dir")).isEqualTo("data");
		assertThat(properties.getProperty("statsfoot.player-source")).isEqualTo("json");
	}

	@Nested
	@SpringBootTest
	class DefaultSource {

		@Autowired
		private ApplicationContext context;

		@Autowired
		private Environment environment;

		@Test
		void usesJsonAdapterWithoutAnyDatabase() {
			assertThat(context.getBean(PlayerRepository.class)).isInstanceOf(JsonPlayerRepository.class);
			assertThat(context.getBeansOfType(JpaPlayerRepository.class)).isEmpty();
			assertThat(context.getBeansOfType(PlayerJpaRepository.class)).isEmpty();
			assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
			assertThat(context.getBeansOfType(EntityManagerFactory.class)).isEmpty();
		}

		@Test
		void dataDirIsDataByDefault() {
			assertThat(environment.getProperty("statsfoot.data-dir")).isEqualTo("data");
		}

	}

	@Nested
	@SpringBootTest
	class ConfiguredDataDir {

		@TempDir
		static Path tempDir;

		@Autowired
		private PlayerRepository repository;

		@DynamicPropertySource
		static void dataDir(DynamicPropertyRegistry registry) throws IOException {
			// Copies of the real files; players.json is cut to its first three entries so the origin is provable.
			Files.copy(TestDataFiles.teamsFile(), tempDir.resolve("teams.json"));
			JsonMapper mapper = JsonMapper.builder().build();
			JsonNode all = mapper.readTree(Files.readAllBytes(TestDataFiles.playersFile()));
			ArrayNode firstThree = mapper.createArrayNode();
			for (JsonNode node : all) {
				if (firstThree.size() < 3) {
					firstThree.add(node);
				}
			}
			Files.write(tempDir.resolve("players.json"), mapper.writeValueAsBytes(firstThree));
			registry.add("statsfoot.data-dir", () -> tempDir.toString());
		}

		@Test
		void readsConfiguredFolderAtStartup() {
			assertThat(repository).isInstanceOf(JsonPlayerRepository.class);
			assertThat(repository.findAll()).hasSize(3);
		}

	}

	@Nested
	@SpringBootTest(properties = {
			"statsfoot.player-source=database",
			"spring.datasource.url=jdbc:h2:mem:configtest;DB_CLOSE_DELAY=-1",
			"spring.jpa.hibernate.ddl-auto=create-drop" })
	@ActiveProfiles("database")
	class DatabaseSource {

		@Autowired
		private ApplicationContext context;

		@Test
		void swapsTheAdapterAndKeepsServiceAndController() {
			assertThat(context.getBean(PlayerRepository.class)).isInstanceOf(JpaPlayerRepository.class);
			assertThat(context.getBeansOfType(JsonPlayerRepository.class)).isEmpty();
			assertThat(context.getBeansOfType(PlayerService.class)).hasSize(1);
			assertThat(context.getBeansOfType(PlayerController.class)).hasSize(1);
			assertThat(context.getBeansOfType(DataSource.class)).hasSize(1);
		}

	}

}
