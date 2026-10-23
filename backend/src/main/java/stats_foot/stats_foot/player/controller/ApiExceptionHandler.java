package stats_foot.stats_foot.player.controller;

import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import stats_foot.stats_foot.player.exception.ComparisonPlayerNotFoundException;
import stats_foot.stats_foot.player.exception.InvalidSearchException;
import stats_foot.stats_foot.player.exception.PlayerDataUnavailableException;
import stats_foot.stats_foot.player.exception.PlayerNotFoundException;
import stats_foot.stats_foot.player.exception.SamePlayerComparisonException;
import stats_foot.stats_foot.player.service.PlayerService;

/** Maps the errors of the player feature (scoped to the player controllers, so the other controllers keep their own handling) to {@code {"error": "<English message>"}} bodies. */
@RestControllerAdvice(basePackageClasses = PlayerController.class)
public class ApiExceptionHandler {

	static final String COMPARISON_PARAMETERS_REQUIRED_MESSAGE = "Both playerA and playerB are required.";
	static final String COMPARISON_IDS_NOT_INTEGERS_MESSAGE = "Player ids must be integers.";

	private static final Set<String> COMPARISON_PARAMETERS = Set.of("playerA", "playerB");

	@ExceptionHandler({ InvalidSearchException.class, MissingServletRequestParameterException.class })
	public ResponseEntity<Map<String, String>> handleInvalidSearch(Exception e) {
		if (e instanceof MissingServletRequestParameterException missing
				&& COMPARISON_PARAMETERS.contains(missing.getParameterName())) {
			return error(HttpStatus.BAD_REQUEST, COMPARISON_PARAMETERS_REQUIRED_MESSAGE);
		}
		return error(HttpStatus.BAD_REQUEST, PlayerService.BLANK_QUERY_MESSAGE);
	}

	@ExceptionHandler({ PlayerNotFoundException.class, MethodArgumentTypeMismatchException.class })
	public ResponseEntity<Map<String, String>> handleNotFound(Exception e) {
		if (e instanceof MethodArgumentTypeMismatchException mismatch
				&& COMPARISON_PARAMETERS.contains(mismatch.getName())) {
			return error(HttpStatus.BAD_REQUEST, COMPARISON_IDS_NOT_INTEGERS_MESSAGE);
		}
		return error(HttpStatus.NOT_FOUND, PlayerService.NOT_FOUND_MESSAGE);
	}

	@ExceptionHandler(SamePlayerComparisonException.class)
	public ResponseEntity<Map<String, String>> handleSamePlayer(SamePlayerComparisonException e) {
		return error(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler(ComparisonPlayerNotFoundException.class)
	public ResponseEntity<Map<String, String>> handleComparisonPlayerNotFound(ComparisonPlayerNotFoundException e) {
		return error(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(PlayerDataUnavailableException.class)
	public ResponseEntity<Map<String, String>> handleUnavailable(PlayerDataUnavailableException e) {
		return error(HttpStatus.SERVICE_UNAVAILABLE, PlayerDataUnavailableException.UNAVAILABLE_MESSAGE);
	}

	private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(Map.of("error", message));
	}

}
