package stats_foot.stats_foot.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import stats_foot.stats_foot.TestDataFile;
import stats_foot.stats_foot.TestDataFile.FilePlayer;
import stats_foot.stats_foot.player.model.Player;
import stats_foot.stats_foot.player.model.PlayerStats;
import stats_foot.stats_foot.player.repository.PlayerRepository;
import tools.jackson.databind.JsonNode;

/**
 * Contract every {@link PlayerRepository} adapter must satisfy. Expected values are read independently
 * from the real data file. Subclasses only provide the repository under test.
 */
abstract class PlayerRepositoryContractTest {

	private static final double DECIMAL_TOLERANCE = 0.01;

	/** Returns a repository loaded with the real players. */
	protected abstract PlayerRepository repository();

	@Test
	void findAllReturnsAllPlayers() {
		assertThat(repository().findAll()).hasSize(1039);
	}

	@Test
	void findAllEqualsTheFileForEveryPlayer() {
		for (Player actual : repository().findAll()) {
			assertSameAsFile(actual, TestDataFile.player(actual.id()));
		}
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 316 })
	void findByIdReturnsIdentityTeamAndTotalsOfTheFile(int id) {
		Player actual = repository().findById(id).orElseThrow();
		assertSameAsFile(actual, TestDataFile.player(id));
	}

	@Test
	void referencePlayersHaveTheDocumentedValues() {
		PlayerRepository repository = repository();
		Player rangel = repository.findById(1).orElseThrow();
		assertThat(rangel.name()).isEqualTo("Raul RANGEL");
		assertThat(rangel.team().name()).isEqualTo("Mexico");
		assertThat(rangel.team().code()).isEqualTo("MEX");
		assertThat(rangel.goalkeeperMatches()).isEqualTo(5);
		assertThat(rangel.stats().matchesPlayed()).isEqualTo(5);
		assertThat(rangel.stats().minutesPlayed()).isEqualTo(438);
		assertThat(rangel.stats().goals()).isZero();
		assertThat(repository.findById(2).orElseThrow().captainMatches()).isEqualTo(3);
		Player wright = repository.findById(316).orElseThrow();
		assertThat(wright.name()).isEqualTo("Haji WRIGHT");
		assertThat(wright.stats().matchesPlayed()).isEqualTo(2);
		assertThat(wright.stats().minutesPlayed()).isZero();
	}

	@Test
	void zeroStaysZero() {
		assertThat(repository().findById(1).orElseThrow().stats().goals()).isEqualTo(0);
	}

	@Test
	void nullStaysNull() {
		assertThat(repository().findById(316).orElseThrow().stats().ratingWeighted()).isNull();
	}

	@Test
	void unknownIdGivesEmpty() {
		assertThat(repository().findById(999999)).isEmpty();
	}

	@Test
	void findByIdReturnsSameRecordAsFindAll() {
		PlayerRepository repository = repository();
		Player fromAll = repository.findAll().stream().filter(p -> p.id() == 2).findFirst().orElseThrow();
		assertThat(repository.findById(2)).contains(fromAll);
	}

	private static void assertSameAsFile(Player actual, FilePlayer expected) {
		String label = "player " + expected.id();
		assertThat(actual.id()).as(label).isEqualTo(expected.id());
		assertThat(actual.name()).as(label).isEqualTo(expected.name());
		assertThat(actual.team().name()).as(label).isEqualTo(expected.teamName());
		assertThat(actual.team().code()).as(label).isEqualTo(expected.teamCode());
		assertThat(actual.goalkeeperMatches()).as(label).isEqualTo(expected.node().get("goalkeeperMatches").asInt());
		assertThat(actual.captainMatches()).as(label).isEqualTo(expected.node().get("captainMatches").asInt());
		JsonNode t = expected.tournament();
		PlayerStats s = actual.stats();
		assertThat(s.matchesPlayed()).as(label + " matchesPlayed").isEqualTo(TestDataFile.intOrNull(t, "matches_played"));
		assertThat(s.matchesStarted()).as(label + " matchesStarted").isEqualTo(TestDataFile.intOrNull(t, "matches_started"));
		assertThat(s.minutesPlayed()).as(label + " minutesPlayed").isEqualTo(TestDataFile.intOrNull(t, "minutes_played"));
		assertThat(s.goals()).as(label + " goals").isEqualTo(TestDataFile.intOrNull(t, "goals"));
		assertThat(s.penaltyGoals()).as(label + " penaltyGoals").isEqualTo(TestDataFile.intOrNull(t, "penalty_goals"));
		assertThat(s.ownGoals()).as(label + " ownGoals").isEqualTo(TestDataFile.intOrNull(t, "own_goals"));
		assertThat(s.yellowCards()).as(label + " yellowCards").isEqualTo(TestDataFile.intOrNull(t, "yellow_cards"));
		assertThat(s.redCards()).as(label + " redCards").isEqualTo(TestDataFile.intOrNull(t, "red_cards"));
		assertThat(s.foulsCommitted()).as(label + " foulsCommitted").isEqualTo(TestDataFile.intOrNull(t, "fouls_committed"));
		assertThat(s.freeKicksWon()).as(label + " freeKicksWon").isEqualTo(TestDataFile.intOrNull(t, "free_kicks_won"));
		assertThat(s.freeKicksConceded()).as(label + " freeKicksConceded")
				.isEqualTo(TestDataFile.intOrNull(t, "free_kicks_conceded"));
		assertThat(s.duelsWon()).as(label + " duelsWon").isEqualTo(TestDataFile.intOrNull(t, "duels_won"));
		assertThat(s.duelsLost()).as(label + " duelsLost").isEqualTo(TestDataFile.intOrNull(t, "duels_lost"));
		assertThat(s.corners()).as(label + " corners").isEqualTo(TestDataFile.intOrNull(t, "corners"));
		assertThat(s.offsides()).as(label + " offsides").isEqualTo(TestDataFile.intOrNull(t, "offsides"));
		assertThat(s.manOfTheMatchCount()).as(label + " manOfTheMatchCount")
				.isEqualTo(TestDataFile.intOrNull(t, "man_of_the_match_count"));
		assertDecimal(s.distanceKm(), TestDataFile.doubleOrNull(t, "distance_km"), label + " distanceKm");
		assertDecimal(s.rating(), TestDataFile.doubleOrNull(t, "rating"), label + " rating");
		assertDecimal(s.ratingWeighted(), TestDataFile.doubleOrNull(t, "rating_weighted"), label + " ratingWeighted");
	}

	private static void assertDecimal(Double actual, Double expected, String label) {
		if (expected == null) {
			assertThat(actual).as(label).isNull();
		} else {
			assertThat(actual).as(label).isNotNull().isCloseTo(expected, within(DECIMAL_TOLERANCE));
		}
	}

}
