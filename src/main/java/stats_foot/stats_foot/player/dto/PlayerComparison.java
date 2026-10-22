package stats_foot.stats_foot.player.dto;

import java.util.List;

import stats_foot.stats_foot.player.model.PlayerRole;

/**
 * A comparison report of two players as exposed by the API. {@code roleWarning} is {@code null} when the roles are equal.
 */
public record PlayerComparison(
		ComparedPlayer playerA,
		ComparedPlayer playerB,
		List<ComparisonLine> lines,
		ComparisonSummary summary,
		String roleWarning,
		String verdict) {

	/** Identity and role of a compared player. */
	public record ComparedPlayer(
			int id,
			String name,
			String teamName,
			String teamCode,
			PlayerRole role) {
	}

	/**
	 * One compared statistic. {@code leader} is {@code null} when the statistic has no leader or the line is unavailable;
	 * {@code difference} is {@code null} only when the line is unavailable.
	 */
	public record ComparisonLine(
			String key,
			String label,
			Number valueA,
			Number valueB,
			Number difference,
			Leader leader,
			boolean available) {
	}

	/** Number of lines led by each player, and of tied lines. */
	public record ComparisonSummary(
			int leadsA,
			int leadsB,
			int ties) {
	}

}
