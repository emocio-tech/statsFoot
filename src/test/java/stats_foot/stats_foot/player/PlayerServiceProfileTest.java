package stats_foot.stats_foot.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import stats_foot.stats_foot.TestDataFile;
import stats_foot.stats_foot.TestDataFile.FilePlayer;
import stats_foot.stats_foot.player.dto.PlayerProfile;
import stats_foot.stats_foot.player.dto.PlayerProfile.PlayerProfileStats;
import stats_foot.stats_foot.player.exception.PlayerNotFoundException;
import stats_foot.stats_foot.player.service.PlayerService;
import tools.jackson.databind.JsonNode;

/** Profile of the service on the real repository (in-memory database imported from the real file). */
@SpringBootTest
@AutoConfigureMockMvc
class PlayerServiceProfileTest {

	private static final List<String> REMOVED_FIELDS = List.of("shirtNumber", "position", "positionDetail",
			"birthDate", "age", "club", "heightCm", "preferredFoot", "captain", "goalkeeperStats", "passAccuracy");

	@Autowired
	private PlayerService service;

	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 316 })
	void identityAndTotalsEqualTheFile(int id) {
		FilePlayer expected = TestDataFile.player(id);
		JsonNode t = expected.tournament();
		PlayerProfile profile = service.getProfile(id);
		assertThat(profile.id()).isEqualTo(id);
		assertThat(profile.name()).isEqualTo(expected.name());
		assertThat(profile.teamCode()).isEqualTo(expected.teamCode());
		assertThat(profile.teamName()).isEqualTo(expected.teamName());
		assertThat(profile.goalkeeperMatches()).isEqualTo(expected.node().get("goalkeeperMatches").asInt());
		assertThat(profile.captainMatches()).isEqualTo(expected.node().get("captainMatches").asInt());
		PlayerProfileStats s = profile.stats();
		assertThat(s.matchesPlayed()).isEqualTo(TestDataFile.intOrNull(t, "matches_played"));
		assertThat(s.matchesStarted()).isEqualTo(TestDataFile.intOrNull(t, "matches_started"));
		assertThat(s.minutesPlayed()).isEqualTo(TestDataFile.intOrNull(t, "minutes_played"));
		assertThat(s.goals()).isEqualTo(TestDataFile.intOrNull(t, "goals"));
		assertThat(s.penaltyGoals()).isEqualTo(TestDataFile.intOrNull(t, "penalty_goals"));
		assertThat(s.ownGoals()).isEqualTo(TestDataFile.intOrNull(t, "own_goals"));
		assertThat(s.yellowCards()).isEqualTo(TestDataFile.intOrNull(t, "yellow_cards"));
		assertThat(s.redCards()).isEqualTo(TestDataFile.intOrNull(t, "red_cards"));
		assertThat(s.foulsCommitted()).isEqualTo(TestDataFile.intOrNull(t, "fouls_committed"));
		assertThat(s.freeKicksWon()).isEqualTo(TestDataFile.intOrNull(t, "free_kicks_won"));
		assertThat(s.freeKicksConceded()).isEqualTo(TestDataFile.intOrNull(t, "free_kicks_conceded"));
		assertThat(s.duelsWon()).isEqualTo(TestDataFile.intOrNull(t, "duels_won"));
		assertThat(s.duelsLost()).isEqualTo(TestDataFile.intOrNull(t, "duels_lost"));
		assertThat(s.corners()).isEqualTo(TestDataFile.intOrNull(t, "corners"));
		assertThat(s.offsides()).isEqualTo(TestDataFile.intOrNull(t, "offsides"));
		assertThat(s.manOfTheMatchCount()).isEqualTo(TestDataFile.intOrNull(t, "man_of_the_match_count"));
		assertThat(s.distanceKm()).isCloseTo(TestDataFile.doubleOrNull(t, "distance_km"), within(0.01));
		assertThat(s.rating()).isCloseTo(TestDataFile.doubleOrNull(t, "rating"), within(0.01));
		Double weighted = TestDataFile.doubleOrNull(t, "rating_weighted");
		if (weighted == null) {
			assertThat(s.ratingWeighted()).isNull();
		} else {
			assertThat(s.ratingWeighted()).isCloseTo(weighted, within(0.01));
		}
	}

	@Test
	void zeroStaysZeroAndNullStaysNull() {
		assertThat(service.getProfile(1).stats().goals()).isEqualTo(0);
		assertThat(service.getProfile(316).stats().ratingWeighted()).isNull();
	}

	@Test
	void profileHasNoRemovedField() {
		List<String> components = Arrays.stream(PlayerProfile.class.getRecordComponents())
				.map(c -> c.getName()).toList();
		assertThat(components).containsExactly("id", "name", "teamCode", "teamName", "goalkeeperMatches",
				"captainMatches", "stats");
		assertThat(components).doesNotContainAnyElementsOf(REMOVED_FIELDS);
		List<String> statComponents = Arrays.stream(PlayerProfileStats.class.getRecordComponents())
				.map(c -> c.getName()).toList();
		assertThat(statComponents).hasSize(19).doesNotContainAnyElementsOf(REMOVED_FIELDS);
	}

	@Test
	void unknownIdIsNotFound() {
		assertThatThrownBy(() -> service.getProfile(999999))
				.isInstanceOf(PlayerNotFoundException.class)
				.hasMessage("Player not found.");
	}

}
