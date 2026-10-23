package stats_foot.stats_foot.player.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import stats_foot.stats_foot.player.dto.Leader;
import stats_foot.stats_foot.player.dto.PlayerComparison.ComparedPlayer;
import stats_foot.stats_foot.player.dto.PlayerComparison.ComparisonLine;
import stats_foot.stats_foot.player.dto.PlayerComparison.ComparisonSummary;

/**
 * Builds the textual verdict of a player comparison. Pure and deterministic.
 */
public final class ComparisonVerdict {

	private static final int MAX_MAIN_DIFFERENCES = 3;

	private ComparisonVerdict() {
	}

	/**
	 * Builds the verdict: leader sentence, main differences sentence (if any) and the role warning (if any).
	 *
	 * @param a           the first player
	 * @param b           the second player
	 * @param lines       the compared statistics, in table order
	 * @param summary     the number of lines led by each player and of ties
	 * @param roleWarning the role warning, or {@code null}
	 * @return the verdict
	 */
	static String build(ComparedPlayer a, ComparedPlayer b, List<ComparisonLine> lines, ComparisonSummary summary,
			String roleWarning) {
		return build(a.name(), b.name(), lines, summary, roleWarning);
	}

	/**
	 * Same verdict for any two compared entities, identified by their display names.
	 *
	 * @param nameA   the name of the first entity
	 * @param nameB   the name of the second entity
	 * @param lines   the compared statistics, in table order
	 * @param summary the number of lines led by each side and of ties
	 * @param warning a closing warning sentence, or {@code null}
	 * @return the verdict
	 */
	public static String build(String nameA, String nameB, List<ComparisonLine> lines, ComparisonSummary summary,
			String warning) {
		List<String> sentences = new ArrayList<>();
		sentences.add(leaderSentence(nameA, nameB, summary));
		String differences = differencesSentence(nameA, nameB, lines);
		if (differences != null) {
			sentences.add(differences);
		}
		if (warning != null) {
			sentences.add(warning);
		}
		return String.join(" ", sentences);
	}

	private static String leaderSentence(String a, String b, ComparisonSummary summary) {
		if (summary.leadsA() == summary.leadsB()) {
			return a + " and " + b + " are level: " + summary.leadsA() + " statistics each ("
					+ summary.ties() + " tied).";
		}
		boolean aLeads = summary.leadsA() > summary.leadsB();
		String leader = aLeads ? a : b;
		String other = aLeads ? b : a;
		int high = Math.max(summary.leadsA(), summary.leadsB());
		int low = Math.min(summary.leadsA(), summary.leadsB());
		return leader + " leads on " + high + " statistics to " + low + " for " + other + " ("
				+ summary.ties() + " tied).";
	}

	private static String differencesSentence(String a, String b, List<ComparisonLine> lines) {
		List<ComparisonLine> candidates = lines.stream()
				.filter(line -> line.available() && (line.leader() == Leader.A || line.leader() == Leader.B))
				.filter(line -> maxMagnitude(line) != 0)
				.sorted(Comparator.comparingDouble(ComparisonVerdict::relativeGap).reversed())
				.limit(MAX_MAIN_DIFFERENCES)
				.toList();
		if (candidates.isEmpty()) {
			return null;
		}
		List<String> entries = new ArrayList<>();
		for (ComparisonLine line : candidates) {
			entries.add(line.label() + " (" + a + " " + format(line.valueA()) + ", " + b + " "
					+ format(line.valueB()) + ")");
		}
		return "The main differences are " + String.join(", ", entries) + ".";
	}

	private static double maxMagnitude(ComparisonLine line) {
		return Math.max(Math.abs(line.valueA().doubleValue()), Math.abs(line.valueB().doubleValue()));
	}

	private static double relativeGap(ComparisonLine line) {
		return Math.abs(line.valueA().doubleValue() - line.valueB().doubleValue()) / maxMagnitude(line);
	}

	private static String format(Number value) {
		if (value instanceof Integer) {
			return value.toString();
		}
		return BigDecimal.valueOf(value.doubleValue()).stripTrailingZeros().toPlainString();
	}
}
