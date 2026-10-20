package stats_foot.stats_foot.player;

/** Raised when the player data cannot be read. The message never exposes a file path. */
public class PlayerDataUnavailableException extends RuntimeException {

	/** Public message shared by every adapter and by the HTTP error body. */
	public static final String UNAVAILABLE_MESSAGE = "Player data is currently unavailable.";

	public PlayerDataUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

}
