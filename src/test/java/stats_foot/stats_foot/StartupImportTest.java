package stats_foot.stats_foot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import stats_foot.stats_foot.database.DatabaseStatus;

/** After start-up, the in-memory database is imported and the status is healthy; the file database is untouched. */
@SpringBootTest
@AutoConfigureMockMvc
class StartupImportTest {

	private static final Path FILE_DATABASE = Path.of("data", "h2", "statsfoot.mv.db");

	/** State of the local file database before the context starts (class loading precedes the context). */
	private static final boolean FILE_EXISTED_BEFORE = Files.exists(FILE_DATABASE);

	private static final long SIZE_BEFORE = sizeOf(FILE_DATABASE);

	private static final long MODIFIED_BEFORE = modifiedOf(FILE_DATABASE);

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private DatabaseStatus databaseStatus;

	@Test
	void databaseIsImportedAtStartup() {
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player", Integer.class)).isEqualTo(1039);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM team", Integer.class)).isEqualTo(48);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM match", Integer.class)).isEqualTo(104);
		assertThat(databaseStatus.isFailed()).isFalse();
	}

	@Test
	void testsNeverUseOrChangeTheFileDatabase() throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:");
		}
		assertThat(Files.exists(FILE_DATABASE)).isEqualTo(FILE_EXISTED_BEFORE);
		if (FILE_EXISTED_BEFORE) {
			assertThat(sizeOf(FILE_DATABASE)).isEqualTo(SIZE_BEFORE);
			assertThat(modifiedOf(FILE_DATABASE)).isEqualTo(MODIFIED_BEFORE);
		}
	}

	private static long sizeOf(Path file) {
		try {
			return Files.exists(file) ? Files.size(file) : -1;
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static long modifiedOf(Path file) {
		try {
			return Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : -1;
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

}
