package stats_foot.stats_foot.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * H2 refuses a database file path that is implicitly relative to the working directory
 * ("data/h2/x"): the default path must start with "./" (or be absolute).
 */
class DatabaseFilePathTest {

	@Test
	void defaultDatabaseFileIsExplicitlyRelativeOrAbsolute() throws IOException {
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources/application.properties"))) {
			properties.load(reader);
		}

		String databaseFile = properties.getProperty("statsfoot.database-file");

		assertThat(databaseFile).isNotBlank();
		boolean explicit = databaseFile.startsWith("./") || databaseFile.startsWith("../")
				|| databaseFile.startsWith("~/") || Path.of(databaseFile).isAbsolute();
		assertThat(explicit).as("H2 needs an explicit './' or absolute path: " + databaseFile).isTrue();
		assertThat(databaseFile).contains("data/h2/");
		assertThat(properties.getProperty("spring.datasource.url")).contains("${statsfoot.database-file}");
	}

}
