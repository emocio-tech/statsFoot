package stats_foot.stats_foot.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestDataFilesTest {

	private static final String EXPECTED_MESSAGE = "data files missing: extract them from the `data` branch";

	@Test
	void failsWithExplicitMessageOnEmptyFolder(@TempDir Path emptyDir) {
		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> TestDataFiles.requireFiles(emptyDir));
		assertEquals(EXPECTED_MESSAGE, error.getMessage());
	}

	@Test
	void failsWhenOnlyOneFileIsPresent(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("players.json"), "[]");
		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> TestDataFiles.requireFiles(dir));
		assertEquals(EXPECTED_MESSAGE, error.getMessage());
	}

	@Test
	void returnsFolderWhenBothFilesArePresent(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("players.json"), "[]");
		Files.writeString(dir.resolve("teams.json"), "[]");
		assertEquals(dir, TestDataFiles.requireFiles(dir));
	}

}
