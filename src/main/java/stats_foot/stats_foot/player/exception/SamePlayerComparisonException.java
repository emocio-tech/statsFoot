package stats_foot.stats_foot.player.exception;

/** Raised when a comparison is requested between a player and themselves. */
public class SamePlayerComparisonException extends RuntimeException {

	public SamePlayerComparisonException() {
		super("The two players must be different.");
	}

}
