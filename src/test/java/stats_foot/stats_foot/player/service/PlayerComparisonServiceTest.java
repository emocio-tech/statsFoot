package stats_foot.stats_foot.player.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import stats_foot.stats_foot.player.dto.Leader;
import stats_foot.stats_foot.player.dto.PlayerComparison;
import stats_foot.stats_foot.player.dto.PlayerComparison.ComparisonLine;
import stats_foot.stats_foot.player.dto.PlayerSearchResult;
import stats_foot.stats_foot.player.dto.PlayerSummary;
import stats_foot.stats_foot.player.exception.AmbiguousPlayerNameException;
import stats_foot.stats_foot.player.exception.BlankComparisonNameException;
import stats_foot.stats_foot.player.exception.ComparisonPlayerNotFoundException;
import stats_foot.stats_foot.player.exception.InvalidSearchException;
import stats_foot.stats_foot.player.exception.SamePlayerComparisonException;
import stats_foot.stats_foot.player.model.Player;
import stats_foot.stats_foot.player.model.PlayerRole;
import stats_foot.stats_foot.player.model.PlayerStats;
import stats_foot.stats_foot.player.model.Team;
import stats_foot.stats_foot.player.repository.PlayerRepository;

class PlayerComparisonServiceTest {

	private static final String WARNING_GK_OUT = "These players do not play the same role (goalkeeper vs outfield player): the comparison is not very relevant.";
	private static final String WARNING_OUT_GK = "These players do not play the same role (outfield player vs goalkeeper): the comparison is not very relevant.";

	private PlayerRepository repository;
	private PlayerService playerService;
	private PlayerComparisonService service;

	@BeforeEach
	void setUp() {
		repository = mock(PlayerRepository.class);
		playerService = mock(PlayerService.class);
		service = new PlayerComparisonService(repository, playerService);
	}

	/** Statistics with every value set to 1 (Integer) or 1.0 (Double). */
	private static PlayerStats stats() {
		return new PlayerStats(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1.0, 1, 1.0, 1.0);
	}

	private static PlayerStats stats(int goals, int yellowCards) {
		return new PlayerStats(1, 1, 1, goals, 1, 1, yellowCards, 1, 1, 1, 1, 1, 1, 1, 1, 1.0, 1, 1.0, 1.0);
	}

	private static Player player(int id, String name, int goalkeeperMatches, PlayerStats stats) {
		return new Player(id, name, new Team(10 + id, "Team " + id, "T" + id), goalkeeperMatches, 0, stats);
	}

	private void given(Player a, Player b) {
		when(repository.findById(a.id())).thenReturn(Optional.of(a));
		when(repository.findById(b.id())).thenReturn(Optional.of(b));
	}

	private PlayerComparison compareStats(PlayerStats statsA, PlayerStats statsB) {
		given(player(1, "Alice", 0, statsA), player(2, "Bob", 0, statsB));
		return service.compare(1, 2);
	}

	private static ComparisonLine line(PlayerComparison report, String key) {
		return report.lines().stream().filter(l -> l.key().equals(key)).findFirst().orElseThrow();
	}

	private static PlayerStats with(UnaryOperator<Object[]> change) {
		Object[] v = { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1.0, 1, 1.0, 1.0 };
		change.apply(v);
		return new PlayerStats((Integer) v[0], (Integer) v[1], (Integer) v[2], (Integer) v[3], (Integer) v[4],
				(Integer) v[5], (Integer) v[6], (Integer) v[7], (Integer) v[8], (Integer) v[9], (Integer) v[10],
				(Integer) v[11], (Integer) v[12], (Integer) v[13], (Integer) v[14], (Double) v[15], (Integer) v[16],
				(Double) v[17], (Double) v[18]);
	}

	@Test
	void compare_twoExistingPlayers_returnsReport() {
		given(player(1, "Alice", 0, stats()), player(2, "Bob", 0, stats()));

		PlayerComparison report = service.compare(1, 2);

		assertThat(report.playerA().id()).isEqualTo(1);
		assertThat(report.playerA().name()).isEqualTo("Alice");
		assertThat(report.playerA().teamName()).isEqualTo("Team 1");
		assertThat(report.playerA().teamCode()).isEqualTo("T1");
		assertThat(report.playerA().role()).isEqualTo(PlayerRole.OUTFIELD_PLAYER);
		assertThat(report.playerB().id()).isEqualTo(2);
		assertThat(report.playerB().name()).isEqualTo("Bob");
		assertThat(report.summary()).isNotNull();
		assertThat(report.roleWarning()).isNull();
		assertThat(report.verdict()).isNotBlank();
	}

	@Test
	void compare_sameId_throwsSamePlayer() {
		assertThatThrownBy(() -> service.compare(3, 3)).isInstanceOf(SamePlayerComparisonException.class);
		verifyNoInteractions(repository);
	}

	@Test
	void compare_unknownA() {
		when(repository.findById(1)).thenReturn(Optional.empty());
		when(repository.findById(2)).thenReturn(Optional.of(player(2, "Bob", 0, stats())));

		assertThatThrownBy(() -> service.compare(1, 2)).isInstanceOf(ComparisonPlayerNotFoundException.class)
				.hasMessage("Player A not found.");
	}

	@Test
	void compare_unknownB() {
		when(repository.findById(1)).thenReturn(Optional.of(player(1, "Alice", 0, stats())));
		when(repository.findById(2)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.compare(1, 2)).isInstanceOf(ComparisonPlayerNotFoundException.class)
				.hasMessage("Player B not found.");
	}

	@Test
	void compare_bothUnknown() {
		when(repository.findById(1)).thenReturn(Optional.empty());
		when(repository.findById(2)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.compare(1, 2)).isInstanceOf(ComparisonPlayerNotFoundException.class)
				.hasMessage("Player A not found.");
	}

	@Test
	void compare_roles_derivedFromGoalkeeperMatches() {
		given(player(1, "Alice", 1, stats()), player(2, "Bob", 0, stats()));

		PlayerComparison report = service.compare(1, 2);

		assertThat(report.playerA().role()).isEqualTo(PlayerRole.GOALKEEPER);
		assertThat(report.playerB().role()).isEqualTo(PlayerRole.OUTFIELD_PLAYER);
	}

	@Test
	void compare_returnsNineteenLinesInOrder() {
		PlayerComparison report = compareStats(stats(), stats());

		assertThat(report.lines()).extracting(ComparisonLine::key).containsExactly("matchesPlayed", "matchesStarted",
				"minutesPlayed", "goals", "penaltyGoals", "ownGoals", "yellowCards", "redCards", "foulsCommitted",
				"freeKicksWon", "freeKicksConceded", "duelsWon", "duelsLost", "corners", "offsides", "distanceKm",
				"manOfTheMatchCount", "rating", "ratingWeighted");
		assertThat(report.lines()).extracting(ComparisonLine::label).containsExactly("Matches played",
				"Matches started", "Minutes played", "Goals", "Penalty goals", "Own goals", "Yellow cards",
				"Red cards", "Fouls committed", "Free kicks won", "Free kicks conceded", "Duels won", "Duels lost",
				"Corners", "Offsides", "Distance (km)", "Man of the match", "Rating",
				"Rating (weighted by minutes)");
	}

	@Test
	void compare_doubleDifferenceRounded() {
		PlayerComparison report = compareStats(with(v -> {
			v[17] = 10.1;
			return v;
		}), with(v -> {
			v[17] = 10.0;
			return v;
		}));

		ComparisonLine rating = line(report, "rating");
		assertThat(rating.difference()).isEqualTo(0.1);
		assertThat(rating.valueA()).isEqualTo(10.1);
		assertThat(rating.valueB()).isEqualTo(10.0);
	}

	@Test
	void doubleGapBelowRounding_isTie() {
		PlayerComparison report = compareStats(with(v -> {
			v[17] = 7.004;
			return v;
		}), with(v -> {
			v[17] = 7.001;
			return v;
		}));

		ComparisonLine rating = line(report, "rating");
		assertThat(rating.difference()).isEqualTo(0.0);
		assertThat(rating.leader()).isEqualTo(Leader.TIE);
	}

	@Test
	void compare_everyLine_matchesDataForAllNineteenStatistics() {
		List<String> keys = List.of("matchesPlayed", "matchesStarted", "minutesPlayed", "goals", "penaltyGoals",
				"ownGoals", "yellowCards", "redCards", "foulsCommitted", "freeKicksWon", "freeKicksConceded",
				"duelsWon", "duelsLost", "corners", "offsides", "distanceKm", "manOfTheMatchCount", "rating",
				"ratingWeighted");
		Object[] a = new Object[19];
		Object[] b = new Object[19];
		for (int i = 0; i < 19; i++) {
			boolean isDouble = i == 15 || i == 17 || i == 18;
			a[i] = isDouble ? (Object) (double) (i + 1) : (Object) (i + 1);
			b[i] = isDouble ? (Object) (double) (2 * (i + 1)) : (Object) (2 * (i + 1));
		}
		PlayerStats statsA = new PlayerStats((Integer) a[0], (Integer) a[1], (Integer) a[2], (Integer) a[3],
				(Integer) a[4], (Integer) a[5], (Integer) a[6], (Integer) a[7], (Integer) a[8], (Integer) a[9],
				(Integer) a[10], (Integer) a[11], (Integer) a[12], (Integer) a[13], (Integer) a[14], (Double) a[15],
				(Integer) a[16], (Double) a[17], (Double) a[18]);
		PlayerStats statsB = new PlayerStats((Integer) b[0], (Integer) b[1], (Integer) b[2], (Integer) b[3],
				(Integer) b[4], (Integer) b[5], (Integer) b[6], (Integer) b[7], (Integer) b[8], (Integer) b[9],
				(Integer) b[10], (Integer) b[11], (Integer) b[12], (Integer) b[13], (Integer) b[14], (Double) b[15],
				(Integer) b[16], (Double) b[17], (Double) b[18]);

		PlayerComparison report = compareStats(statsA, statsB);

		for (int i = 0; i < 19; i++) {
			ComparisonLine l = line(report, keys.get(i));
			assertThat(l.valueA()).as(keys.get(i)).isEqualTo(a[i]);
			assertThat(l.valueB()).as(keys.get(i)).isEqualTo(b[i]);
			Object expected = a[i] instanceof Integer x ? (Object) (x - (Integer) b[i])
					: (Object) ((Double) a[i] - (Double) b[i]);
			assertThat(l.difference()).as(keys.get(i)).isEqualTo(expected);
		}
		assertThat(report.playerB().teamName()).isEqualTo("Team 2");
		assertThat(report.playerB().teamCode()).isEqualTo("T2");
	}

	@Test
	void goals_higherLeads() {
		PlayerComparison report = compareStats(stats(5, 1), stats(3, 1));

		ComparisonLine goals = line(report, "goals");
		assertThat(goals.available()).isTrue();
		assertThat(goals.valueA()).isEqualTo(5);
		assertThat(goals.valueB()).isEqualTo(3);
		assertThat(goals.difference()).isEqualTo(2);
		assertThat(goals.leader()).isEqualTo(Leader.A);
	}

	private static final List<String> LOWER_BETTER_KEYS = List.of("ownGoals", "yellowCards", "redCards",
			"foulsCommitted", "freeKicksConceded", "duelsLost", "offsides");

	@ParameterizedTest
	@ValueSource(strings = { "ownGoals", "yellowCards", "redCards", "foulsCommitted", "freeKicksConceded",
			"duelsLost", "offsides" })
	void yellowCards_lowerLeads(String key) {
		int index = List.of("matchesPlayed", "matchesStarted", "minutesPlayed", "goals", "penaltyGoals", "ownGoals",
				"yellowCards", "redCards", "foulsCommitted", "freeKicksWon", "freeKicksConceded", "duelsWon",
				"duelsLost", "corners", "offsides").indexOf(key);
		assertThat(LOWER_BETTER_KEYS).contains(key);

		PlayerComparison report = compareStats(with(v -> {
			v[index] = 4;
			return v;
		}), with(v -> {
			v[index] = 2;
			return v;
		}));

		ComparisonLine l = line(report, key);
		assertThat(l.leader()).isEqualTo(Leader.B);
		assertThat(l.difference()).isEqualTo(2);
	}

	@Test
	void equalValues_tie() {
		PlayerComparison report = compareStats(stats(), stats());

		ComparisonLine goals = line(report, "goals");
		assertThat(goals.leader()).isEqualTo(Leader.TIE);
		assertThat(goals.difference()).isEqualTo(0);
		assertThat(line(report, "yellowCards").leader()).isEqualTo(Leader.TIE);
	}

	@Test
	void noLeaderStats_neverDesignateLeader() {
		PlayerComparison report = compareStats(with(v -> {
			v[0] = 5;
			v[1] = 5;
			v[2] = 500;
			v[15] = 50.0;
			return v;
		}), stats());

		for (String key : List.of("matchesPlayed", "matchesStarted", "minutesPlayed", "distanceKm")) {
			ComparisonLine l = line(report, key);
			assertThat(l.leader()).as(key).isNull();
			assertThat(l.available()).as(key).isTrue();
			assertThat(l.difference()).as(key).isNotNull();
		}
		assertThat(line(report, "matchesPlayed").difference()).isEqualTo(4);
		assertThat(line(report, "distanceKm").difference()).isEqualTo(49.0);
	}

	@Test
	void nullValue_lineNotAvailable() {
		PlayerComparison report = compareStats(with(v -> {
			v[3] = null;
			return v;
		}), stats());

		ComparisonLine goals = line(report, "goals");
		assertThat(goals.available()).isFalse();
		assertThat(goals.valueA()).isNull();
		assertThat(goals.valueB()).isEqualTo(1);
		assertThat(goals.difference()).isNull();
		assertThat(goals.leader()).isNull();
		// 19 lines: 4 without leader, 1 unavailable -> 14 ties, no leads
		assertThat(report.summary().ties()).isEqualTo(14);
		assertThat(report.summary().leadsA() + report.summary().leadsB() + report.summary().ties()).isEqualTo(14);
	}

	@Test
	void summary_countsMatchLines() {
		PlayerComparison report = compareStats(with(v -> {
			v[3] = 5; // goals: A leads
			v[6] = 4; // yellow cards: B leads (lower)
			v[11] = 0; // duels won: B leads
			return v;
		}), with(v -> {
			v[3] = 3;
			v[6] = 2;
			v[11] = 3;
			return v;
		}));

		long a = report.lines().stream().filter(l -> l.leader() == Leader.A).count();
		long b = report.lines().stream().filter(l -> l.leader() == Leader.B).count();
		long t = report.lines().stream().filter(l -> l.leader() == Leader.TIE).count();
		assertThat(report.summary().leadsA()).isEqualTo((int) a).isEqualTo(1);
		assertThat(report.summary().leadsB()).isEqualTo((int) b).isEqualTo(2);
		assertThat(report.summary().ties()).isEqualTo((int) t);
	}

	@Test
	void roleWarning_whenRolesDiffer() {
		given(player(1, "Alice", 2, stats()), player(2, "Bob", 0, stats()));
		assertThat(service.compare(1, 2).roleWarning()).isEqualTo(WARNING_GK_OUT);
		assertThat(service.compare(1, 2).lines()).hasSize(19);

		given(player(3, "Carl", 0, stats()), player(4, "Dan", 2, stats()));
		PlayerComparison reversed = service.compare(3, 4);
		assertThat(reversed.roleWarning()).isEqualTo(WARNING_OUT_GK);
		assertThat(reversed.lines()).hasSize(19);
		assertThat(reversed.verdict()).contains(WARNING_OUT_GK);
	}

	@Test
	void roleWarning_nullWhenSameRole() {
		given(player(1, "Alice", 2, stats()), player(2, "Bob", 3, stats()));
		assertThat(service.compare(1, 2).roleWarning()).isNull();
	}

	private static PlayerSummary summary(int id) {
		return new PlayerSummary(id, "Player " + id, "T" + id, "Team " + id);
	}

	private void givenSearch(String name, PlayerSummary... players) {
		when(playerService.search(name))
				.thenReturn(new PlayerSearchResult(List.of(players), players.length, false));
	}

	@Test
	void compareByName_resolvesNamesWithCaseAndAccents() {
		given(player(1, "Alice", 0, stats(3, 1)), player(2, "Bob", 0, stats(1, 2)));
		givenSearch("ÁLICE", summary(1));
		givenSearch("bob", summary(2));

		PlayerComparison report = service.compareByName("ÁLICE", "bob");

		assertThat(report).isEqualTo(service.compare(1, 2));
	}

	@Test
	void compareByName_blankName() {
		givenSearch("Bob", summary(2));
		givenSearch("Alice", summary(1));
		when(playerService.search(" ")).thenThrow(new InvalidSearchException("Please enter a player name."));

		assertThatThrownBy(() -> service.compareByName(" ", "Bob"))
				.isInstanceOf(BlankComparisonNameException.class)
				.hasMessage("Player A name must not be blank.");
		assertThatThrownBy(() -> service.compareByName("Alice", " "))
				.isInstanceOf(BlankComparisonNameException.class)
				.hasMessage("Player B name must not be blank.");
	}

	@Test
	void compareByName_unknownName() {
		givenSearch("Alice", summary(1));
		when(playerService.search("Nobody")).thenReturn(new PlayerSearchResult(List.of(), 0, false));

		assertThatThrownBy(() -> service.compareByName("Nobody", "Alice"))
				.isInstanceOf(ComparisonPlayerNotFoundException.class)
				.hasMessage("Player A not found.");
		assertThatThrownBy(() -> service.compareByName("Alice", "Nobody"))
				.isInstanceOf(ComparisonPlayerNotFoundException.class)
				.hasMessage("Player B not found.");
	}

	@Test
	void compareByName_severalMatches_listsAtMostTenCandidates() {
		List<PlayerSummary> ten = IntStream.rangeClosed(1, 10).mapToObj(PlayerComparisonServiceTest::summary).toList();
		when(playerService.search("p")).thenReturn(new PlayerSearchResult(ten, 13, false));
		when(playerService.search("q"))
				.thenReturn(new PlayerSearchResult(List.of(summary(1), summary(2), summary(3)), 3, false));
		givenSearch("Alice", summary(1));

		assertThatThrownBy(() -> service.compareByName("p", "Alice"))
				.isInstanceOf(AmbiguousPlayerNameException.class)
				.hasMessage("Several players match the name of player A: Player 1 (T1), Player 2 (T2), Player 3 (T3), "
						+ "Player 4 (T4), Player 5 (T5), Player 6 (T6), Player 7 (T7), Player 8 (T8), Player 9 (T9), "
						+ "Player 10 (T10) and 3 more. Please be more precise.");
		assertThatThrownBy(() -> service.compareByName("Alice", "q"))
				.isInstanceOf(AmbiguousPlayerNameException.class)
				.hasMessage("Several players match the name of player B: Player 1 (T1), Player 2 (T2), Player 3 (T3). "
						+ "Please be more precise.");
	}

	@Test
	void compareByName_severalMatches_exactlyTen_noMoreSuffix() {
		List<PlayerSummary> ten = IntStream.rangeClosed(1, 10).mapToObj(PlayerComparisonServiceTest::summary).toList();
		when(playerService.search("p")).thenReturn(new PlayerSearchResult(ten, 10, false));
		givenSearch("Alice", summary(1));

		assertThatThrownBy(() -> service.compareByName("p", "Alice"))
				.isInstanceOf(AmbiguousPlayerNameException.class)
				.hasMessage("Several players match the name of player A: Player 1 (T1), Player 2 (T2), Player 3 (T3), "
						+ "Player 4 (T4), Player 5 (T5), Player 6 (T6), Player 7 (T7), Player 8 (T8), Player 9 (T9), "
						+ "Player 10 (T10). Please be more precise.");
	}

	@Test
	void compareByName_severalMatches_eleven_andOneMore() {
		List<PlayerSummary> ten = IntStream.rangeClosed(1, 10).mapToObj(PlayerComparisonServiceTest::summary).toList();
		when(playerService.search("p")).thenReturn(new PlayerSearchResult(ten, 11, false));
		givenSearch("Alice", summary(1));

		assertThatThrownBy(() -> service.compareByName("p", "Alice"))
				.isInstanceOf(AmbiguousPlayerNameException.class)
				.hasMessage("Several players match the name of player A: Player 1 (T1), Player 2 (T2), Player 3 (T3), "
						+ "Player 4 (T4), Player 5 (T5), Player 6 (T6), Player 7 (T7), Player 8 (T8), Player 9 (T9), "
						+ "Player 10 (T10) and 1 more. Please be more precise.");
	}

	@Test
	void compareByName_sameResolvedPlayer_throwsSamePlayer() {
		givenSearch("alice", summary(1));
		givenSearch("ALICE", summary(1));

		assertThatThrownBy(() -> service.compareByName("alice", "ALICE"))
				.isInstanceOf(SamePlayerComparisonException.class);
	}

	private static PlayerSummary summary(int id, String name) {
		return new PlayerSummary(id, name, "T" + id, "Team " + id);
	}

	private void givenSearch(String query, int total, PlayerSummary... players) {
		when(playerService.search(query)).thenReturn(new PlayerSearchResult(List.of(players), total, false));
	}

	@Test
	void compareByName_exactNameAmongSeveralMatches_usesExactPlayer() {
		given(player(1, "Danilo", 0, stats(3, 1)), player(2, "Bob", 0, stats(1, 2)));
		givenSearch("Danilo", 2, summary(3, "Danilo Santos"), summary(1, "Danilo"));
		givenSearch("Bob", summary(2));

		PlayerComparison report = service.compareByName("Danilo", "Bob");

		assertThat(report).isEqualTo(service.compare(1, 2));
	}

	@Test
	void compareByName_exactMatchIgnoresCaseAccentsAndSpaces() {
		given(player(1, "Raúl Jiménez", 0, stats(3, 1)), player(2, "Bob", 0, stats(1, 2)));
		givenSearch("  RAUL   jimenez ", 2, summary(3, "Raul Jimenez Junior"), summary(1, "Raúl Jiménez"));
		givenSearch("Bob", summary(2));

		PlayerComparison report = service.compareByName("  RAUL   jimenez ", "Bob");

		assertThat(report).isEqualTo(service.compare(1, 2));
	}

	@Test
	void compareByName_severalExactNames_stillAmbiguous() {
		givenSearch("Danilo", 3, summary(1, "Danilo"), summary(4, "danilo"), summary(3, "Danilo Santos"));
		givenSearch("Bob", summary(2));

		assertThatThrownBy(() -> service.compareByName("Danilo", "Bob"))
				.isInstanceOf(AmbiguousPlayerNameException.class)
				.hasMessage("Several players match the name of player A: Danilo (T1), danilo (T4), "
						+ "Danilo Santos (T3). Please be more precise.");
	}

	@Test
	void compareByName_noExactName_stillAmbiguous() {
		givenSearch("Dan", 2, summary(1, "Danilo"), summary(3, "Danilo Santos"));
		givenSearch("Bob", summary(2));

		assertThatThrownBy(() -> service.compareByName("Dan", "Bob"))
				.isInstanceOf(AmbiguousPlayerNameException.class)
				.hasMessage("Several players match the name of player A: Danilo (T1), Danilo Santos (T3). "
						+ "Please be more precise.");
	}

	@Test
	void compareByName_resolvesAThenB() {
		when(playerService.search("Nobody")).thenReturn(new PlayerSearchResult(List.of(), 0, false));
		when(playerService.search(" ")).thenThrow(new InvalidSearchException("Please enter a player name."));

		assertThatThrownBy(() -> service.compareByName("Nobody", " "))
				.isInstanceOf(ComparisonPlayerNotFoundException.class)
				.hasMessage("Player A not found.");
	}
}
