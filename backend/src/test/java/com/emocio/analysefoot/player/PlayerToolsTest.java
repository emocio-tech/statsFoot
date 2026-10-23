package com.emocio.analysefoot.player;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import com.emocio.analysefoot.player.dto.PlayerComparison;
import com.emocio.analysefoot.player.dto.PlayerLookupResult;
import com.emocio.analysefoot.player.dto.PlayerProfile;
import com.emocio.analysefoot.player.dto.PlayerQueryResult;
import com.emocio.analysefoot.player.dto.PlayerSearchResult;
import com.emocio.analysefoot.player.dto.PlayerSummary;
import com.emocio.analysefoot.player.exception.ComparisonPlayerNotFoundException;
import com.emocio.analysefoot.player.exception.PlayerNotFoundException;
import com.emocio.analysefoot.player.model.PlayerStat;
import com.emocio.analysefoot.player.service.PlayerComparisonService;
import com.emocio.analysefoot.player.service.PlayerService;
import com.emocio.analysefoot.player.tool.PlayerTools;

class PlayerToolsTest {

	private final PlayerService playerService = mock(PlayerService.class);
	private final PlayerComparisonService playerComparisonService = mock(PlayerComparisonService.class);
	private final PlayerTools tools = new PlayerTools(playerService, playerComparisonService);

	private static PlayerSummary summary(int id, String name) {
		return new PlayerSummary(id, name, "T" + id, "Team " + id);
	}

	private static PlayerSearchResult found(PlayerSummary... players) {
		return new PlayerSearchResult(List.of(players), players.length, false);
	}

	@Test
	void searchPlayers_singleMatch_returnsProfile() {
		PlayerProfile profile = mock(PlayerProfile.class);
		when(playerService.search("mbappe")).thenReturn(found(summary(7, "Kylian MBAPPE")));
		when(playerService.getProfile(7)).thenReturn(profile);

		PlayerLookupResult result = tools.searchPlayers("mbappe");

		assertSame(profile, result.profile());
		assertNull(result.candidates());
	}

	@Test
	void searchPlayers_exactNameAmongSeveral_returnsProfile() {
		PlayerProfile profile = mock(PlayerProfile.class);
		when(playerService.search("Silva")).thenReturn(found(summary(1, "SILVA"), summary(2, "Silva Jr")));
		when(playerService.getProfile(1)).thenReturn(profile);

		assertSame(profile, tools.searchPlayers("Silva").profile());
	}

	@Test
	void searchPlayers_severalClosestNames_returnsCandidates() {
		PlayerSearchResult search = found(summary(1, "Bruno Silva"), summary(2, "Silva Jr"));
		when(playerService.search("silv")).thenReturn(search);

		PlayerLookupResult result = tools.searchPlayers("silv");

		assertSame(search, result.candidates());
		assertNull(result.profile());
	}

	@Test
	void searchPlayers_noMatch_throwsPlayerNotFound() {
		when(playerService.search("zzz")).thenReturn(found());

		assertThrows(PlayerNotFoundException.class, () -> tools.searchPlayers("zzz"));
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
	void comparePlayers_delegatesToService() {
		PlayerComparison comparison = mock(PlayerComparison.class);
		when(playerComparisonService.compareByName("mbappe", "haaland")).thenReturn(comparison);

		assertSame(comparison, tools.comparePlayers("mbappe", "haaland"));
	}

	@Test
	void comparePlayers_playerNotFound_tellsToUseSearchPlayers() {
		when(playerComparisonService.compareByName("mbappe", "unknown"))
				.thenThrow(new ComparisonPlayerNotFoundException("Player \"unknown\" not found."));

		ComparisonPlayerNotFoundException e = assertThrows(ComparisonPlayerNotFoundException.class,
				() -> tools.comparePlayers("mbappe", "unknown"));

		assertTrue(e.getMessage().startsWith("Player \"unknown\" not found."));
		assertTrue(e.getMessage().contains("searchPlayers"));
	}
}
