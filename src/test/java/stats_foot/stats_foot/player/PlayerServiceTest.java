package stats_foot.stats_foot.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import stats_foot.stats_foot.TestDataFile;
import stats_foot.stats_foot.TestDataFile.FilePlayer;
import stats_foot.stats_foot.player.dto.PlayerSearchResult;
import stats_foot.stats_foot.player.dto.PlayerSummary;
import stats_foot.stats_foot.player.exception.InvalidSearchException;
import stats_foot.stats_foot.player.service.PlayerService;
import stats_foot.stats_foot.player.util.NameNormalizer;

/** Search of the service on the real repository (in-memory database imported from the real file). */
@SpringBootTest
@AutoConfigureMockMvc
class PlayerServiceTest {

	private static final String BLANK_MESSAGE = "Please enter a player name.";

	@Autowired
	private PlayerService service;

	/** Expected matches computed from the file: same normalisation, sorted by normalised name then id. */
	private static List<FilePlayer> expectedMatches(String query) {
		String normalized = NameNormalizer.normalize(query);
		return TestDataFile.players().stream()
				.filter(p -> NameNormalizer.normalize(p.name()).contains(normalized))
				.sorted(Comparator.comparing((FilePlayer p) -> NameNormalizer.normalize(p.name()))
						.thenComparing(FilePlayer::id))
				.toList();
	}

	private static List<Integer> ids(PlayerSearchResult result) {
		return result.players().stream().map(PlayerSummary::id).toList();
	}

	@ParameterizedTest
	@ValueSource(strings = { "rangel", "RANGEL", "  raul   rangel  " })
	void findsRangelWhateverTheCaseAndSpacing(String query) {
		PlayerSearchResult result = service.search(query);
		assertThat(result.total()).isEqualTo(1);
		assertThat(result.truncated()).isFalse();
		PlayerSummary summary = result.players().get(0);
		assertThat(summary).isEqualTo(new PlayerSummary(1, "Raul RANGEL", "MEX", "Mexico"));
	}

	@Test
	void soGivesExactlyFiftyAndIsNotTruncated() {
		PlayerSearchResult result = service.search("so");
		assertThat(result.players()).hasSize(50);
		assertThat(result.total()).isEqualTo(50);
		assertThat(result.truncated()).isFalse();
		assertThat(result.total()).isEqualTo(expectedMatches("so").size());
	}

	@Test
	void teGivesFiftyOutOfFiftyOneAndIsTruncated() {
		PlayerSearchResult result = service.search("te");
		assertThat(result.players()).hasSize(50);
		assertThat(result.total()).isEqualTo(51);
		assertThat(result.truncated()).isTrue();
		assertThat(ids(result)).isEqualTo(expectedMatches("te").stream().limit(50).map(FilePlayer::id).toList());
	}

	@Test
	void aGivesFiftyOutOfNineHundredAndThirteenSortedByNormalisedName() {
		PlayerSearchResult result = service.search("a");
		List<FilePlayer> expected = expectedMatches("a");
		assertThat(result.players()).hasSize(50);
		assertThat(result.total()).isEqualTo(913);
		assertThat(expected).hasSize(913);
		assertThat(result.truncated()).isTrue();
		assertThat(ids(result)).isEqualTo(expected.stream().limit(50).map(FilePlayer::id).toList());
		List<String> normalized = result.players().stream().map(p -> NameNormalizer.normalize(p.name())).toList();
		assertThat(normalized).isSorted();
	}

	@Test
	void sortIsStableBetweenCalls() {
		assertThat(service.search("a")).isEqualTo(service.search("a"));
		assertThat(service.search("te")).isEqualTo(service.search("te"));
	}

	@Test
	void summariesCarryTheTeamOfTheFile() {
		for (PlayerSummary summary : service.search("so").players()) {
			FilePlayer expected = TestDataFile.player(summary.id());
			assertThat(summary.name()).isEqualTo(expected.name());
			assertThat(summary.teamCode()).isEqualTo(expected.teamCode());
			assertThat(summary.teamName()).isEqualTo(expected.teamName());
		}
	}

	@Test
	void noMatchGivesEmptyResultWithoutException() {
		PlayerSearchResult result = service.search("xyzxyz");
		assertThat(result.players()).isEmpty();
		assertThat(result.total()).isZero();
		assertThat(result.truncated()).isFalse();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   ", "\t", " " })
	void blankQueryIsRejected(String query) {
		assertThatThrownBy(() -> service.search(query))
				.isInstanceOf(InvalidSearchException.class)
				.hasMessage(BLANK_MESSAGE);
	}

}
