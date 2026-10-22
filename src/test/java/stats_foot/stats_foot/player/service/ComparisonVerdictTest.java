package stats_foot.stats_foot.player.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import stats_foot.stats_foot.player.dto.Leader;
import stats_foot.stats_foot.player.dto.PlayerComparison.ComparedPlayer;
import stats_foot.stats_foot.player.dto.PlayerComparison.ComparisonLine;
import stats_foot.stats_foot.player.dto.PlayerComparison.ComparisonSummary;
import stats_foot.stats_foot.player.model.PlayerRole;

/**
 * Unit tests of {@link ComparisonVerdict}.
 */
class ComparisonVerdictTest {

	private static final ComparedPlayer PLAYER_A = new ComparedPlayer(1, "Alice", "France", "FRA", PlayerRole.OUTFIELD_PLAYER);
	private static final ComparedPlayer PLAYER_B = new ComparedPlayer(2, "Bob", "Brazil", "BRA", PlayerRole.OUTFIELD_PLAYER);

	private static ComparisonLine line(String key, Number a, Number b, Leader leader, boolean available) {
		return new ComparisonLine(key, "Label " + key, a, b, null, leader, available);
	}

	@Test
	void aLeads_namesAAndCounts() {
		List<ComparisonLine> lines = List.of(line("x", 10, 5, Leader.A, true));

		String verdict = ComparisonVerdict.build(PLAYER_A, PLAYER_B, lines, new ComparisonSummary(5, 2, 1), null);

		assertThat(verdict).startsWith("Alice leads on 5 statistics to 2 for Bob (1 tied).");
	}

	@Test
	void bLeads_namesBAndCounts() {
		String verdict = ComparisonVerdict.build(PLAYER_A, PLAYER_B, List.of(), new ComparisonSummary(2, 5, 1), null);

		assertThat(verdict).isEqualTo("Bob leads on 5 statistics to 2 for Alice (1 tied).");
	}

	@Test
	void equalCounts_saysLevel() {
		String verdict = ComparisonVerdict.build(PLAYER_A, PLAYER_B, List.of(), new ComparisonSummary(3, 3, 2), null);

		assertThat(verdict).isEqualTo("Alice and Bob are level: 3 statistics each (2 tied).");
	}

	@Test
	void atMostThreeMainDifferences() {
		List<ComparisonLine> lines = List.of(
				line("l1", 10, 5, Leader.A, true), // gap 0.5
				line("l2", 1.5, 3.0, Leader.B, true), // gap 0.5
				line("l3", 9, 10, Leader.B, true), // gap 0.1
				line("l4", 0, 4, Leader.B, true), // gap 1.0
				line("l5", 8, 2, Leader.A, true), // gap 0.75
				line("l6", 100, 99, Leader.A, true), // gap 0.01
				line("tie", 5, 5, Leader.TIE, true),
				line("none", 1, 100, null, true),
				line("unavailable", 1, 100, Leader.B, false),
				line("zero", 0, 0, Leader.A, true));

		String verdict = ComparisonVerdict.build(PLAYER_A, PLAYER_B, lines, new ComparisonSummary(3, 3, 1), null);

		assertThat(verdict).isEqualTo("Alice and Bob are level: 3 statistics each (1 tied). "
				+ "The main differences are Label l4 (Alice 0, Bob 4), Label l5 (Alice 8, Bob 2), Label l1 (Alice 10, Bob 5).");
	}

	@Test
	void tiedGapsKeepTableOrder_andDoublesAreFormatted() {
		List<ComparisonLine> lines = List.of(
				line("l1", 10, 5, Leader.A, true),
				line("l2", 1.50, 3.0, Leader.B, true));

		String verdict = ComparisonVerdict.build(PLAYER_A, PLAYER_B, lines, new ComparisonSummary(1, 1, 0), null);

		assertThat(verdict).endsWith("The main differences are Label l1 (Alice 10, Bob 5), Label l2 (Alice 1.5, Bob 3).");
	}

	@Test
	void noCandidate_omitsSentence2() {
		List<ComparisonLine> lines = List.of(
				line("tie", 5, 5, Leader.TIE, true),
				line("none", 1, 2, null, true),
				line("unavailable", 1, 2, Leader.B, false),
				line("zero", 0, 0, Leader.A, true));

		String verdict = ComparisonVerdict.build(PLAYER_A, PLAYER_B, lines, new ComparisonSummary(0, 0, 1), null);

		assertThat(verdict).isEqualTo("Alice and Bob are level: 0 statistics each (1 tied).");
	}

	@Test
	void differentRoles_appendsWarning() {
		List<ComparisonLine> lines = List.of(line("x", 10, 5, Leader.A, true));
		ComparisonSummary summary = new ComparisonSummary(1, 0, 0);

		String withWarning = ComparisonVerdict.build(PLAYER_A, PLAYER_B, lines, summary, "Different roles.");
		String withoutWarning = ComparisonVerdict.build(PLAYER_A, PLAYER_B, lines, summary, null);

		assertThat(withWarning).endsWith(" Different roles.").startsWith(withoutWarning);
		assertThat(withoutWarning).doesNotContain("Different roles.");
	}

	@Test
	void sameInput_sameVerdict() {
		List<ComparisonLine> lines = List.of(
				line("l1", 10, 5, Leader.A, true),
				line("l2", 1.5, 3.0, Leader.B, true));
		ComparisonSummary summary = new ComparisonSummary(1, 1, 0);

		assertThat(ComparisonVerdict.build(PLAYER_A, PLAYER_B, lines, summary, "W"))
				.isEqualTo(ComparisonVerdict.build(PLAYER_A, PLAYER_B, lines, summary, "W"));
	}
}
