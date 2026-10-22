package stats_foot.stats_foot.team.exception;

/** Raised when one of the two teams of a comparison does not exist. */
public class TeamNotFoundException extends RuntimeException {

	public TeamNotFoundException(String message) {
		super(message);
	}

}
