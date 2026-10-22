package com.emocio.analysefoot.tools;

import com.emocio.analysefoot.tools.StatTools.Row;
import com.emocio.analysefoot.tools.StatTools.StatResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.emocio.analysefoot.dto.MatchSummary;
import com.emocio.analysefoot.service.RatingQueryService;
import java.time.OffsetDateTime;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Les tools stat et calculate du mode libre : calcul exact, agrégats, filtres, listes fermées. */
class StatToolsTest {

    private static final List<Row> PLAYERS = List.of(
            new Row("Kylian MBAPPE", "France", BigDecimal.valueOf(10)),
            new Row("Antoine GRIEZMANN", "France", BigDecimal.valueOf(4)),
            new Row("Lamine YAMAL", "Spain", BigDecimal.valueOf(6)),
            new Row("Gardien SANS BUT", "Spain", null));

    private final RatingQueryService ratings = mock(RatingQueryService.class);
    private final StatTools tools = new StatTools(mock(JdbcClient.class), ratings);

    @Test
    void calculatesExactlyWithPrecedenceAndParentheses() {
        assertThat(tools.calculate("1 + 2 * 3")).isEqualTo("7");
        assertThat(tools.calculate("(14 + 9) / 2")).isEqualTo("11.5");
        assertThat(tools.calculate("0,1 + 0.2")).isEqualTo("0.3");
        assertThat(tools.calculate("-(3 - 5) * 2")).isEqualTo("4");
        assertThat(tools.calculate("10 % 4")).isEqualTo("2");
        assertThat(tools.calculate("100 * 7 / 8")).isEqualTo("87.5");
    }

    @Test
    void refusesInvalidExpressionsAndAnythingButArithmetic() {
        for (String bad : new String[] {"", "  ", "1 / 0", "2 +", "(1 + 2", "abs(3)", "System.exit(0)", "1 2", "x"}) {
            assertThatThrownBy(() -> tools.calculate(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> tools.calculate("1+".repeat(200) + "1")).hasMessageContaining("trop longue");
    }

    @Test
    void sumAverageMaxMinAndCountSkipUnknownValues() {
        assertThat(agg("SUM", null, null).value()).isEqualByComparingTo("20");
        assertThat(agg("AVG", null, null).value()).isEqualByComparingTo("6.6667");
        assertThat(agg("MAX", null, null).value()).isEqualByComparingTo("10");
        assertThat(agg("MIN", null, null).value()).isEqualByComparingTo("4");
        StatResult count = agg("COUNT", null, null);
        assertThat(count.value()).isEqualByComparingTo("3");
        assertThat(count.considered()).isEqualTo(4);
    }

    @Test
    void filtersByTeamAndByNameIgnoringAccentsAndCase() {
        assertThat(agg("SUM", "france", null).value()).isEqualByComparingTo("14");
        assertThat(agg("SUM", null, "mbappe").value()).isEqualByComparingTo("10");
        assertThat(agg("SUM", null, "Kylian").considered()).isEqualTo(1);
        assertThat(agg("SUM", "inconnue", null).considered()).isZero();
    }

    @Test
    void listIsSortedAndLimited() {
        StatResult top = StatTools.compute("PLAYER", "goals", "NONE", PLAYERS, null, null, null, 2);
        assertThat(top.rows()).extracting(Row::name).containsExactly("Kylian MBAPPE", "Lamine YAMAL");
        StatResult ascending = StatTools.compute("PLAYER", "goals", "NONE", PLAYERS, null, null, "ASC", 1);
        assertThat(ascending.rows()).extracting(Row::name).containsExactly("Antoine GRIEZMANN");
        assertThat(StatTools.compute("PLAYER", "goals", "NONE", PLAYERS, null, null, null, 999).rows()).hasSize(4);
    }

    @Test
    void fictitiousStatisticsAreFlagged() {
        assertThat(StatTools.compute("PLAYER", "corners", "SUM", PLAYERS, null, null, null, null).fictitious()).isTrue();
        assertThat(agg("SUM", null, null).fictitious()).isFalse();
    }

    @Test
    void unknownScopeMetricAndAggregateAreRejectedBeforeAnyQuery() {
        assertThatThrownBy(() -> tools.stat("COACH", "goals", null, null, null, null, null))
                .hasMessageContaining("scope inconnu");
        assertThatThrownBy(() -> tools.stat("PLAYER", "salary", null, null, null, null, null))
                .hasMessageContaining("Statistique inconnue");
        assertThatThrownBy(() -> tools.stat("PLAYER", "goals; DROP TABLE player", null, null, null, null, null))
                .hasMessageContaining("Statistique inconnue");
        assertThatThrownBy(() -> tools.stat("TEAM", "goals_for", "MEDIAN", null, null, null, null))
                .hasMessageContaining("aggregate inconnu");
    }

    @Test
    void exposesTheThreeTools() {
        assertThat(java.util.Arrays.stream(tools.statToolsProvider().getToolCallbacks()).map(t -> t.getToolDefinition().name()))
                .containsExactlyInAnyOrder("stat", "calculate", "matches");
    }

    private static MatchSummary match(int id, String stage, String team1, String team2) {
        return new MatchSummary(id, stage, null, OffsetDateTime.parse("2026-06-11T19:00:00Z"), "Stade", "Ville", 1, team1, 2,
                team2, 2, 1, false, null, null, null, null);
    }

    @Test
    void matchesAreFilteredByTeamAndStageAndLimited() {
        when(ratings.findMatches()).thenReturn(List.of(match(1, "GROUP", "France", "Spain"),
                match(2, "GROUP", "Brazil", "France"), match(3, "FINAL", "Spain", "Brazil")));

        assertThat(tools.matches("france", null, null)).extracting(MatchSummary::id).containsExactly(1, 2);
        assertThat(tools.matches(null, "final", null)).extracting(MatchSummary::id).containsExactly(3);
        assertThat(tools.matches("spain", "GROUP", null)).extracting(MatchSummary::id).containsExactly(1);
        assertThat(tools.matches(null, null, 1)).hasSize(1);
        assertThatThrownBy(() -> tools.matches(null, "QUARTER", null)).hasMessageContaining("Phase inconnue");
    }

    private static StatResult agg(String aggregate, String team, String name) {
        return StatTools.compute("PLAYER", "goals", aggregate, PLAYERS, name, team, null, null);
    }
}
