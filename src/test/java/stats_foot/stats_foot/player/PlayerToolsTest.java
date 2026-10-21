package stats_foot.stats_foot.player;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import stats_foot.stats_foot.player.dto.PlayerProfile;
import stats_foot.stats_foot.player.dto.PlayerQueryResult;
import stats_foot.stats_foot.player.dto.PlayerSearchResult;
import stats_foot.stats_foot.player.model.PlayerStat;
import stats_foot.stats_foot.player.service.PlayerService;
import stats_foot.stats_foot.player.tool.PlayerTools;

class PlayerToolsTest {

	private final PlayerService playerService = mock(PlayerService.class);
	private final PlayerTools tools = new PlayerTools(playerService);

	@Test
	void searchPlayers_delegatesToService() {
		PlayerSearchResult result = new PlayerSearchResult(java.util.List.of(), 0, false);
		when(playerService.search("mbappe")).thenReturn(result);

		assertSame(result, tools.searchPlayers("mbappe"));
	}

	@Test
	void findPlayers_delegatesToService_descendingByDefault() {
		PlayerQueryResult result = new PlayerQueryResult(java.util.List.of(), 0, false);
		when(playerService.query("France", true, null, null, null, PlayerStat.GOALS, true, 3)).thenReturn(result);

		assertSame(result, tools.findPlayers("France", true, null, null, null, PlayerStat.GOALS, null, 3));
	}

	@Test
	void findPlayers_passesAscendingOrder() {
		PlayerQueryResult result = new PlayerQueryResult(java.util.List.of(), 0, false);
		when(playerService.query(null, null, null, null, null, PlayerStat.GOALS, false, null)).thenReturn(result);

		assertSame(result, tools.findPlayers(null, null, null, null, null, PlayerStat.GOALS, false, null));
	}

	@Test
	void getPlayerProfile_delegatesToService() {
		PlayerProfile profile = mock(PlayerProfile.class);
		when(playerService.getProfile(7)).thenReturn(profile);

		assertSame(profile, tools.getPlayerProfile(7));
	}

}
