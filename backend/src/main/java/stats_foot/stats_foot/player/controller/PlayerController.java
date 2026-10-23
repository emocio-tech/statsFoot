package stats_foot.stats_foot.player.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import stats_foot.stats_foot.player.dto.PlayerComparison;
import stats_foot.stats_foot.player.dto.PlayerProfile;
import stats_foot.stats_foot.player.dto.PlayerSearchResult;
import stats_foot.stats_foot.player.service.PlayerComparisonService;
import stats_foot.stats_foot.player.service.PlayerService;

/** REST endpoints of the player feature. */
@RestController
@RequestMapping("/api/players")
public class PlayerController {

	private final PlayerService playerService;

	private final PlayerComparisonService playerComparisonService;

	public PlayerController(PlayerService playerService, PlayerComparisonService playerComparisonService) {
		this.playerService = playerService;
		this.playerComparisonService = playerComparisonService;
	}

	@GetMapping
	public PlayerSearchResult search(@RequestParam("name") String name) {
		return playerService.search(name);
	}

	@GetMapping("/compare")
	public PlayerComparison compare(@RequestParam("playerA") int playerA, @RequestParam("playerB") int playerB) {
		PlayerComparison result = playerComparisonService.compare(playerA, playerB);
		
		return playerComparisonService.compare(playerA, playerB);
	}

	@GetMapping("/{id}")
	public PlayerProfile getProfile(@PathVariable("id") int id) {
		return playerService.getProfile(id);
	}

}
