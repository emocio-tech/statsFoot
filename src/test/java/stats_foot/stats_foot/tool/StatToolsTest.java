package stats_foot.stats_foot.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import stats_foot.stats_foot.tool.StatTools.MatchSummary;
import stats_foot.stats_foot.tool.StatTools.RankResult;
import stats_foot.stats_foot.tool.StatTools.RankedEntry;
import stats_foot.stats_foot.tool.StatTools.Row;
import stats_foot.stats_foot.tool.StatTools.StatResult;

/** The stat, rank, matches and calculate tools: pure logic on rows, then SQL on the in-memory database. */
@SpringBootTest
class StatToolsTest {

	@Autowired
	private StatTools tools;

	private static Row row(String name, String team, String value) {
		return new Row(name, team, value == null ? null : new BigDecimal(value));
	}

	private static final List<Row> ROWS = List.of(row("Ana Lima", "Brazil", "3"), row("Léo Silva", "Brazil", "5"),
			row("Max Roy", "France", null), row("Jo Roy", "France", "5"));

	@Test
	void calculate_evaluatesExactly() {
		assertThat(tools.calculate("(14 + 9) / 2")).isEqualTo("11.5");
		assertThat(tools.calculate("0,1 + 0.2")).isEqualTo("0.3");
		assertThat(tools.calculate("-3 * (2 + 1) % 4")).isEqualTo("-1");
	}

	@Test
	void calculate_rejectsInvalidInput() {
		assertThatThrownBy(() -> tools.calculate("1 / 0")).hasMessage("Division by zero.");
		assertThatThrownBy(() -> tools.calculate("2 +")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> tools.calculate("Runtime.exec()")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> tools.calculate(" ")).hasMessage("Empty expression.");
	}

	@Test
	void compute_listPutsUnknownValuesLastWhateverTheOrder() {
		StatResult desc = StatTools.compute("PLAYER", "goals", "NONE", ROWS, null, null, "DESC", 10);
		StatResult asc = StatTools.compute("PLAYER", "goals", "NONE", ROWS, null, null, "ASC", 10);

		assertThat(desc.rows()).extracting(Row::value).startsWith(new BigDecimal("5"), new BigDecimal("5"))
				.endsWith((BigDecimal) null);
		assertThat(asc.rows().get(0).name()).isEqualTo("Ana Lima");
		assertThat(asc.rows().get(3).value()).isNull();
	}

	@Test
	void compute_filtersByNameAndTeamIgnoringAccentsAndCase() {
		StatResult byName = StatTools.compute("PLAYER", "goals", "NONE", ROWS, "leo", null, null, null);
		StatResult byTeam = StatTools.compute("PLAYER", "goals", "COUNT", ROWS, null, "FRA", null, null);

		assertThat(byName.rows()).extracting(Row::name).containsExactly("Léo Silva");
		assertThat(byTeam.considered()).isEqualTo(2);
		assertThat(byTeam.value()).isEqualByComparingTo("1");
	}

	@Test
	void compute_aggregatesIgnoreUnknownValues() {
		assertThat(StatTools.compute("PLAYER", "goals", "SUM", ROWS, null, null, null, null).value())
				.isEqualByComparingTo("13");
		assertThat(StatTools.compute("PLAYER", "goals", "AVG", ROWS, null, null, null, null).value())
				.isEqualByComparingTo("4.3333");
		assertThat(StatTools.compute("PLAYER", "goals", "COUNT", ROWS, null, null, null, null).value())
				.isEqualByComparingTo("3");
		assertThat(StatTools.compute("PLAYER", "goals", "MAX", ROWS, null, null, null, null).value())
				.isEqualByComparingTo("5");
		StatResult none = StatTools.compute("PLAYER", "goals", "MIN", ROWS, "Max", null, null, null);
		assertThat(none.value()).isNull();
		assertThat(none.note()).isEqualTo("no value on the kept lines");
	}

	@Test
	void stat_unknownInputsAreRejected() {
		assertThatThrownBy(() -> tools.stat("COACH", "goals", null, null, null, null, null))
				.hasMessageContaining("Unknown scope");
		assertThatThrownBy(() -> tools.stat("PLAYER", "name; DROP TABLE player", null, null, null, null, null))
				.hasMessageContaining("Unknown statistic");
		assertThatThrownBy(() -> tools.stat("PLAYER", "goals", "MEDIAN", null, null, null, null))
				.hasMessageContaining("Unknown aggregate");
	}

	@Test
	void stat_readsTheViewsForPlayersAndTeams() {
		StatResult goals = tools.stat("player", "goals", "SUM", null, null, null, null);
		StatResult top = tools.stat("TEAM", "goal_difference", null, null, null, "DESC", 3);

		assertThat(goals.value()).isPositive();
		assertThat(top.rows()).hasSize(3);
		assertThat(top.rows()).extracting(Row::value).isSortedAccordingTo(java.util.Comparator.reverseOrder());
	}

	@Test
	void rank_keepsTiesAndReturnsTheWinners() {
		RankResult result = tools.rank("PLAYER", "goals", null);

		assertThat(result.order()).isEqualTo("DESC");
		assertThat(result.top()).isNotEmpty().extracting(RankedEntry::rank).isSorted();
		assertThat(result.winners()).isNotEmpty().allMatch(e -> e.rank() == 1)
				.allMatch(e -> e.value().compareTo(result.top().get(0).value()) == 0);
	}

	@Test
	void rank_ascendingStartsWithTheLowestValue() {
		RankResult result = tools.rank("TEAM", "points", "asc");

		assertThat(result.order()).isEqualTo("ASC");
		assertThat(result.top().get(0).value()).isLessThanOrEqualTo(result.top().get(result.top().size() - 1).value());
	}

	@Test
	void rank_rejectsUnknownStatistic() {
		assertThatThrownBy(() -> tools.rank("TEAM", "assists", null)).hasMessageContaining("Unknown statistic");
	}

	@Test
	void matches_filtersByStageAndTeamAndLimit() {
		List<MatchSummary> all = tools.matches(null, null, 200);
		List<MatchSummary> finals = tools.matches(null, "final", null);
		String team = all.get(0).team1();
		List<MatchSummary> ofTeam = tools.matches(team, null, null);

		assertThat(all).hasSizeLessThanOrEqualTo(StatTools.MAX_MATCHES);
		assertThat(finals).isNotEmpty().extracting(MatchSummary::stage).containsOnly("FINAL");
		assertThat(ofTeam).isNotEmpty().allMatch(m -> team.equals(m.team1()) || team.equals(m.team2()));
		assertThat(tools.matches(null, null, 2)).hasSize(2);
		assertThatThrownBy(() -> tools.matches(null, "SEMI", null)).hasMessageContaining("Unknown stage");
	}

}
