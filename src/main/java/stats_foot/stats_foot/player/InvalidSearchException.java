package stats_foot.stats_foot.player;

/** Raised when the search query is null or blank. */
public class InvalidSearchException extends RuntimeException {

	public InvalidSearchException(String message) {
		super(message);
	}

}
