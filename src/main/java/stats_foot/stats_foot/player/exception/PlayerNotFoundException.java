package stats_foot.stats_foot.player.exception;

/** Raised when no player has the requested id. */
public class PlayerNotFoundException extends RuntimeException {

	public PlayerNotFoundException(String message) {
		super(message);
	}

}
