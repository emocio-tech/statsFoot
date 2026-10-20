package stats_foot.stats_foot.player;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps the errors of the player feature to {@code {"error": "<English message>"}} bodies. */
@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler({ InvalidSearchException.class, MissingServletRequestParameterException.class })
	public ResponseEntity<Map<String, String>> handleInvalidSearch(Exception e) {
		return error(HttpStatus.BAD_REQUEST, PlayerService.BLANK_QUERY_MESSAGE);
	}

	@ExceptionHandler(PlayerNotFoundException.class)
	public ResponseEntity<Map<String, String>> handleNotFound(PlayerNotFoundException e) {
		return error(HttpStatus.NOT_FOUND, PlayerService.NOT_FOUND_MESSAGE);
	}

	@ExceptionHandler(PlayerDataUnavailableException.class)
	public ResponseEntity<Map<String, String>> handleUnavailable(PlayerDataUnavailableException e) {
		return error(HttpStatus.SERVICE_UNAVAILABLE, PlayerDataUnavailableException.UNAVAILABLE_MESSAGE);
	}

	private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(Map.of("error", message));
	}

}
