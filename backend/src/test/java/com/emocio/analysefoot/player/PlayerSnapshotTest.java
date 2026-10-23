package com.emocio.analysefoot.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.emocio.analysefoot.FullDataTest;
import com.emocio.analysefoot.TestDataFile;
import com.emocio.analysefoot.TestDataFile.FilePlayer;
import com.emocio.analysefoot.player.dto.PlayerProfile;
import com.emocio.analysefoot.player.dto.PlayerProfile.PlayerProfileStats;
import com.emocio.analysefoot.player.exception.InvalidSearchException;
import com.emocio.analysefoot.player.exception.PlayerNotFoundException;
import com.emocio.analysefoot.player.service.PlayerService;

/** The player service on the real application, compared with the reference snapshot {@code data/donneesStats.json}. */
@FullDataTest
class PlayerSnapshotTest {

	/** Column of the "tournament" block of the file to statistic of the profile. */
	private static final Map<String, Function<PlayerProfileStats, Number>> STATS = Map.ofEntries(
			Map.entry("matches_played", PlayerProfileStats::matchesPlayed),
			Map.entry("matches_started", PlayerProfileStats::matchesStarted),
			Map.entry("minutes_played", PlayerProfileStats::minutesPlayed),
			Map.entry("goals", PlayerProfileStats::goals),
			Map.entry("penalty_goals", PlayerProfileStats::penaltyGoals),
			Map.entry("own_goals", PlayerProfileStats::ownGoals),
			Map.entry("yellow_cards", PlayerProfileStats::yellowCards),
			Map.entry("red_cards", PlayerProfileStats::redCards),
			Map.entry("fouls_committed", PlayerProfileStats::foulsCommitted),
			Map.entry("free_kicks_won", PlayerProfileStats::freeKicksWon),
			Map.entry("free_kicks_conceded", PlayerProfileStats::freeKicksConceded),
			Map.entry("duels_won", PlayerProfileStats::duelsWon),
			Map.entry("duels_lost", PlayerProfileStats::duelsLost),
			Map.entry("corners", PlayerProfileStats::corners),
			Map.entry("offsides", PlayerProfileStats::offsides),
			Map.entry("distance_km", PlayerProfileStats::distanceKm),
			Map.entry("man_of_the_match_count", PlayerProfileStats::manOfTheMatchCount),
			Map.entry("rating", PlayerProfileStats::rating),
			Map.entry("rating_weighted", PlayerProfileStats::ratingWeighted));

	@Autowired
	private PlayerService playerService;

	@Autowired
	private JdbcClient jdbcClient;

	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 316, 664 })
	void profileMatchesTheSnapshot(int id) {
		FilePlayer expected = TestDataFile.player(id);
		PlayerProfile profile = playerService.getProfile(id);

		assertThat(profile.name()).isEqualTo(expected.name());
		assertThat(profile.teamCode()).isEqualTo(expected.teamCode());
		assertThat(profile.teamName()).isEqualTo(expected.teamName());
		assertThat(profile.goalkeeperMatches()).isEqualTo(expected.node().get("goalkeeperMatches").asInt());
		assertThat(profile.captainMatches()).isEqualTo(expected.node().get("captainMatches").asInt());
		STATS.forEach((column, getter) -> {
			Number actual = getter.apply(profile.stats());
			var node = expected.tournament().get(column);
			if (node == null || node.isNull()) {
				assertThat(actual).as(column).isNull();
			} else {
				assertThat(actual.doubleValue()).as(column).isEqualTo(node.asDouble());
			}
		});
	}

	@Test
	void rangelHasFiveGoalkeeperMatchesAndNoGoal() {
		PlayerProfile profile = playerService.getProfile(1);

		assertThat(profile.goalkeeperMatches()).isEqualTo(5);
		assertThat(profile.stats().goals()).isZero();
	}

	@Test
	void nullIsKeptForAPlayerWhoDidNotPlay() {
		PlayerProfile profile = playerService.getProfile(316);

		assertThat(profile.stats().minutesPlayed()).isZero();
		assertThat(profile.stats().ratingWeighted()).isNull();
	}

	@Test
	void additionalMetricsMatchTheTournamentView() {
		List<Map<String, Object>> rows = jdbcClient.sql("""
				SELECT player_id, assists, decisive_goals, second_yellows, direct_reds, fair_play_points
				FROM v_player_tournament_stats WHERE competition_id = (SELECT MAX(id) FROM competition)
				""").query().listOfRows();

		assertThat(rows).isNotEmpty();
		int withAssists = 0;
		for (Map<String, Object> row : rows) {
			PlayerProfileStats stats = playerService.getProfile(((Number) row.get("player_id")).intValue()).stats();
			assertThat(stats.assists()).isEqualTo(intOrNull(row.get("assists")));
			assertThat(stats.decisiveGoals()).isEqualTo(intOrNull(row.get("decisive_goals")));
			assertThat(stats.secondYellows()).isEqualTo(intOrNull(row.get("second_yellows")));
			assertThat(stats.directReds()).isEqualTo(intOrNull(row.get("direct_reds")));
			assertThat(stats.fairPlayPoints()).isEqualTo(intOrNull(row.get("fair_play_points")));
			if (stats.assists() != null && stats.assists() > 0) {
				withAssists++;
			}
		}
		assertThat(withAssists).as("players with at least one assist").isPositive();
	}

	@Test
	void searchCountsMatchTheSnapshot() {
		assertThat(playerService.search("rangel").players()).hasSize(1);
		assertThat(playerService.search("rangel").players().get(0).name()).isEqualTo("Raul RANGEL");
		assertThat(playerService.search("so").total()).isEqualTo(50);
		assertThat(playerService.search("so").truncated()).isFalse();
		assertThat(playerService.search("te").total()).isEqualTo(51);
		assertThat(playerService.search("te").truncated()).isTrue();
		assertThat(playerService.search("a").total()).isEqualTo(913);
		assertThat(playerService.search("a").players()).hasSize(50);
		assertThat(playerService.search("xyzxyz").total()).isZero();
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "  " })
	void blankNameIsRejected(String name) {
		assertThatThrownBy(() -> playerService.search(name)).isInstanceOf(InvalidSearchException.class)
				.hasMessage(PlayerService.BLANK_QUERY_MESSAGE);
	}

	@Test
	void unknownIdIsNotFound() {
		assertThatThrownBy(() -> playerService.getProfile(999999)).isInstanceOf(PlayerNotFoundException.class);
	}

	private static Integer intOrNull(Object value) {
		return value == null ? null : ((Number) value).intValue();
	}

}
