package stats_foot.stats_foot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** No file under src/main refers to the former players.json / teams.json sources (no Spring). */
class NoJsonSourceTest {

	private static final Path MAIN = Path.of("src", "main");

	@Test
	void noMainFileMentionsTheFormerJsonSources() throws IOException {
		List<String> offenders = new ArrayList<>();
		try (Stream<Path> files = Files.walk(MAIN)) {
			for (Path file : files.filter(Files::isRegularFile).toList()) {
				String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
				if (text.contains("players.json") || text.contains("teams.json")) {
					offenders.add(file.toString());
				}
			}
		}
		assertThat(offenders).isEmpty();
	}

	@Test
	void theScanActuallyVisitsFiles() throws IOException {
		try (Stream<Path> files = Files.walk(MAIN)) {
			assertThat(files.filter(Files::isRegularFile).count()).isGreaterThan(10);
		}
	}

}
