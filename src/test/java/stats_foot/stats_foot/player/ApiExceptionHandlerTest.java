package stats_foot.stats_foot.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import stats_foot.stats_foot.player.exception.PlayerNotFoundException;
import stats_foot.stats_foot.player.service.PlayerService;

@WebMvcTest(PlayerController.class)
class ApiExceptionHandlerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PlayerService playerService;

	@Test
	void invalidSearchGivesBadRequest() throws Exception {
		when(playerService.search(any())).thenThrow(new InvalidSearchException("anything"));
		mockMvc.perform(get("/api/players").param("name", " "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Please enter a player name."));
	}

	@Test
	void playerNotFoundGivesNotFound() throws Exception {
		when(playerService.getProfile(any())).thenThrow(new PlayerNotFoundException("anything"));
		mockMvc.perform(get("/api/players/P-1"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("Player not found."));
	}

	@Test
	void unavailableDataGivesServiceUnavailableWithoutPathNorTrace() throws Exception {
		Exception cause = new IOException("C:\\secret\\data\\players.json (No such file)");
		when(playerService.search(any()))
				.thenThrow(new PlayerDataUnavailableException("C:\\secret\\data\\players.json", cause));
		String body = mockMvc.perform(get("/api/players").param("name", "abc"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error").value("Player data is currently unavailable."))
				.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("secret").doesNotContain("players.json").doesNotContain("Exception")
				.doesNotContain("\\").doesNotContain("at stats_foot");
	}

	@Test
	void unavailableDataOnProfileGivesServiceUnavailable() throws Exception {
		when(playerService.getProfile(any()))
				.thenThrow(new PlayerDataUnavailableException("x", new RuntimeException("y")));
		mockMvc.perform(get("/api/players/P-0001"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error").value("Player data is currently unavailable."));
	}

	@Test
	void missingNameParameterGivesBadRequest() throws Exception {
		mockMvc.perform(get("/api/players"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Please enter a player name."));
	}

}
