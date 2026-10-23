package stats_foot.stats_foot.team.exception;

/** Raised when a team name given to a comparison by name matches several teams. */
public class AmbiguousTeamNameException extends RuntimeException {

	public AmbiguousTeamNameException(String message) {
		super(message);
	}

}
