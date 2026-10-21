package com.emocio.analysefoot.controller;

import com.emocio.analysefoot.dto.PlayerTournamentStats;
import com.emocio.analysefoot.dto.TeamTournamentStats;
import com.emocio.analysefoot.service.TournamentStatsQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TournamentStatsController.class)
class TournamentStatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TournamentStatsQueryService statsQueryService;

    @Test
    void playerStatsDefaultToLatestCompetition() throws Exception {
        when(statsQueryService.findPlayerStats(null)).thenReturn(List.of(new PlayerTournamentStats(
                709, "Erling HAALAND", 20, "Norway", 5, 5, 476, 7, 1, 0, 0, 0,
                null, null, null, null, null, null, null, null, null, null, 0,
                new BigDecimal("8.5"), new BigDecimal("8.3"), null)));

        mockMvc.perform(get("/api/stats/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].player").value("Erling HAALAND"))
                .andExpect(jsonPath("$[0].goals").value(7))
                .andExpect(jsonPath("$[0].foulsCommitted").isEmpty())
                .andExpect(jsonPath("$[0].ratingWeighted").value(8.3))
                .andExpect(jsonPath("$[0].fictitiousFields[0]").value("foulsCommitted"))
                .andExpect(jsonPath("$[0].fictitiousFields.length()").value(10));
        verify(statsQueryService).findPlayerStats(null);
    }

    @Test
    void teamStatsPassCompetition() throws Exception {
        when(statsQueryService.findTeamStats(2)).thenReturn(List.of(new TeamTournamentStats(
                29, "Spain", 8, 7, 1, 0, 22, 14, 1, 6, 0, -6,
                95, 110, 95, 400, 380, 52, 12, new BigDecimal("880.40"), 8, new BigDecimal("8.9"), null)));

        mockMvc.perform(get("/api/stats/teams").param("competitionId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].team").value("Spain"))
                .andExpect(jsonPath("$[0].corners").value(52))
                .andExpect(jsonPath("$[0].rating").value(8.9))
                .andExpect(jsonPath("$[0].fictitiousFields.length()").value(8));
        verify(statsQueryService).findTeamStats(2);
    }

    @Test
    void invalidCompetitionIsRejected() throws Exception {
        mockMvc.perform(get("/api/stats/teams").param("competitionId", "abc"))
                .andExpect(status().isBadRequest());
    }
}
