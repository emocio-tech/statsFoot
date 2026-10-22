package stats_foot.stats_foot.team.controller;

import java.util.Map;
import java.util.Set;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import stats_foot.stats_foot.player.exception.InvalidSearchException;
import stats_foot.stats_foot.team.exception.SameTeamComparisonException;
import stats_foot.stats_foot.team.exception.TeamNotFoundException;
import stats_foot.stats_foot.team.service.TeamService;

/**
 * Maps the errors of the team endpoints to {@code {"error": "<English message>"}} bodies. It runs before the
 * player handler, which would answer with player messages for the shared exception types.
 */
@RestControllerAdvice(assignableTypes = TeamController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TeamExceptionHandler {

	static final String COMPARISON_PARAMETERS_REQUIRED_MESSAGE = "Both teamA and teamB are required.";
	static final String COMPARISON_IDS_NOT_INTEGERS_MESSAGE = "Team ids must be integers.";

	private static final Set<String> COMPARISON_PARAMETERS = Set.of("teamA", "teamB");

	@ExceptionHandler({ InvalidSearchException.class, MissingServletRequestParameterException.class })
	public ResponseEntity<Map<String, String>> handleInvalidRequest(Exception e) {
		if (e instanceof MissingServletRequestParameterException missing
				&& COMPARISON_PARAMETERS.contains(missing.getParameterName())) {
			return error(HttpStatus.BAD_REQUEST, COMPARISON_PARAMETERS_REQUIRED_MESSAGE);
		}
		return error(HttpStatus.BAD_REQUEST, TeamService.BLANK_QUERY_MESSAGE);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
		return error(HttpStatus.BAD_REQUEST, COMPARISON_IDS_NOT_INTEGERS_MESSAGE);
	}

	@ExceptionHandler(SameTeamComparisonException.class)
	public ResponseEntity<Map<String, String>> handleSameTeam(SameTeamComparisonException e) {
		return error(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler(TeamNotFoundException.class)
	public ResponseEntity<Map<String, String>> handleNotFound(TeamNotFoundException e) {
		return error(HttpStatus.NOT_FOUND, e.getMessage());
	}

	private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(Map.of("error", message));
	}

}
