package stats_foot.stats_foot.team.exception;

/** Raised when a comparison is requested between a team and itself. */
public class SameTeamComparisonException extends RuntimeException {

	public SameTeamComparisonException() {
		super("The two teams must be different.");
	}

}
