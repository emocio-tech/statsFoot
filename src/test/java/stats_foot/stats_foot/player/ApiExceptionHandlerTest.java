package stats_foot.stats_foot.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import stats_foot.stats_foot.player.controller.PlayerController;
import stats_foot.stats_foot.player.exception.InvalidSearchException;
import stats_foot.stats_foot.player.exception.PlayerDataUnavailableException;
import stats_foot.stats_foot.player.exception.ComparisonPlayerNotFoundException;
import stats_foot.stats_foot.player.exception.PlayerNotFoundException;
import stats_foot.stats_foot.player.exception.SamePlayerComparisonException;
import stats_foot.stats_foot.player.service.PlayerComparisonService;
import stats_foot.stats_foot.player.service.PlayerService;

@WebMvcTest(PlayerController.class)
class ApiExceptionHandlerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PlayerService playerService;

	@MockitoBean
	private PlayerComparisonService playerComparisonService;

	@Test
	void invalidSearchGivesBadRequest() throws Exception {
		when(playerService.search(any())).thenThrow(new InvalidSearchException("anything"));
		mockMvc.perform(get("/api/players").param("name", " "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Please enter a player name."));
	}

	@Test
	void playerNotFoundGivesNotFound() throws Exception {
		when(playerService.getProfile(anyInt())).thenThrow(new PlayerNotFoundException("anything"));
		mockMvc.perform(get("/api/players/999999"))
				.andExpect(status().isNotFound())
				.andExpect(content().json("{\"error\":\"Player not found.\"}", true));
	}

	@Test
	void nonNumericIdGivesNotFound() throws Exception {
		mockMvc.perform(get("/api/players/abc"))
				.andExpect(status().isNotFound())
				.andExpect(content().json("{\"error\":\"Player not found.\"}", true));
	}

	@Test
	void unavailableDataGivesServiceUnavailableWithoutPathNorTrace() throws Exception {
		Exception cause = new IOException("C:\\secret\\data\\donneesStats.json (No such file)");
		when(playerService.search(any()))
				.thenThrow(new PlayerDataUnavailableException("C:\\secret\\data\\donneesStats.json", cause));
		String body = mockMvc.perform(get("/api/players").param("name", "abc"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error").value("Player data is currently unavailable."))
				.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("secret").doesNotContain("donneesStats").doesNotContain("Exception")
				.doesNotContain("\\").doesNotContain("/").doesNotContain("at stats_foot");
	}

	@Test
	void unavailableDataOnProfileGivesServiceUnavailable() throws Exception {
		when(playerService.getProfile(anyInt()))
				.thenThrow(new PlayerDataUnavailableException("x", new RuntimeException("y")));
		mockMvc.perform(get("/api/players/1"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().json("{\"error\":\"Player data is currently unavailable.\"}", true));
	}

	@Test
	void missingNameParameterGivesBadRequest() throws Exception {
		mockMvc.perform(get("/api/players"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Please enter a player name."));
	}

	@Test
	void samePlayerComparisonGivesBadRequest() throws Exception {
		when(playerComparisonService.compare(anyInt(), anyInt())).thenThrow(new SamePlayerComparisonException());
		mockMvc.perform(get("/api/players/compare").param("playerA", "1").param("playerB", "1"))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("{\"error\":\"The two players must be different.\"}", true));
	}

	@Test
	void unknownPlayerAGivesNamedNotFound() throws Exception {
		when(playerComparisonService.compare(anyInt(), anyInt()))
				.thenThrow(new ComparisonPlayerNotFoundException("Player A not found."));
		mockMvc.perform(get("/api/players/compare").param("playerA", "999999").param("playerB", "2"))
				.andExpect(status().isNotFound())
				.andExpect(content().json("{\"error\":\"Player A not found.\"}", true));
	}

	@Test
	void unknownPlayerBGivesNamedNotFound() throws Exception {
		when(playerComparisonService.compare(anyInt(), anyInt()))
				.thenThrow(new ComparisonPlayerNotFoundException("Player B not found."));
		mockMvc.perform(get("/api/players/compare").param("playerA", "1").param("playerB", "999999"))
				.andExpect(status().isNotFound())
				.andExpect(content().json("{\"error\":\"Player B not found.\"}", true));
	}

	@Test
	void missingPlayerBGivesBadRequest() throws Exception {
		mockMvc.perform(get("/api/players/compare").param("playerA", "1"))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("{\"error\":\"Both playerA and playerB are required.\"}", true));
	}

	@Test
	void nonNumericPlayerAGivesBadRequest() throws Exception {
		mockMvc.perform(get("/api/players/compare").param("playerA", "abc").param("playerB", "2"))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("{\"error\":\"Player ids must be integers.\"}", true));
	}

	@Test
	void unavailableDataOnComparisonGivesServiceUnavailable() throws Exception {
		when(playerComparisonService.compare(anyInt(), anyInt()))
				.thenThrow(new PlayerDataUnavailableException("x", new RuntimeException("y")));
		mockMvc.perform(get("/api/players/compare").param("playerA", "1").param("playerB", "2"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().json("{\"error\":\"Player data is currently unavailable.\"}", true));
	}

}
