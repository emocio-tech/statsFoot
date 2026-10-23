package stats_foot.stats_foot.player.exception;

/** Raised when one of the two players of a comparison does not exist. */
public class ComparisonPlayerNotFoundException extends PlayerNotFoundException {

	public ComparisonPlayerNotFoundException(String message) {
		super(message);
	}

}
