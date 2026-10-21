package stats_foot.stats_foot.player;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Test helper that locates the local {@code data/} folder (never committed) and fails with an
 * explicit message when the data files are missing.
 */
public final class TestDataFiles {

	static final String DATA_DIR_PROPERTY = "statsfoot.data-dir";
	static final String DEFAULT_DATA_DIR = "data";
	static final String MISSING_MESSAGE = "data files missing: extract them from the `data` branch";

	private TestDataFiles() {
	}

	/** Returns the data folder from {@code statsfoot.data-dir}, or {@code data} relative to the project root. */
	public static Path dataDir() {
		Path dir = Paths.get(System.getProperty(DATA_DIR_PROPERTY, DEFAULT_DATA_DIR));
		return requireFiles(dir.toAbsolutePath().normalize());
	}

	/** Checks that {@code players.json} and {@code teams.json} exist in the folder and returns it. */
	static Path requireFiles(Path dir) {
		if (!Files.isRegularFile(dir.resolve("players.json")) || !Files.isRegularFile(dir.resolve("teams.json"))) {
			throw new IllegalStateException(MISSING_MESSAGE);
		}
		return dir;
	}

	public static Path playersFile() {
		return dataDir().resolve("players.json");
	}

	public static Path teamsFile() {
		return dataDir().resolve("teams.json");
	}

}
