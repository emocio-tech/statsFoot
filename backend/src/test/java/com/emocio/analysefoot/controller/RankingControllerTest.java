package com.emocio.analysefoot.controller;

import com.emocio.analysefoot.dto.PlayerRanking;
import com.emocio.analysefoot.dto.TeamRanking;
import com.emocio.analysefoot.service.RatingQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RankingController.class)
class RankingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RatingQueryService ratingQueryService;

    @Test
    void playerRankingUsesDefaultParameters() throws Exception {
        when(ratingQueryService.findPlayerRanking(3, 20)).thenReturn(List.of(
                new PlayerRanking(709, "Erling HAALAND", "Norway", 5, new BigDecimal("8.46"), 7, 476)));

        mockMvc.perform(get("/api/rankings/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Erling HAALAND"))
                .andExpect(jsonPath("$[0].averageRating").value(8.46));
        verify(ratingQueryService).findPlayerRanking(3, 20);
    }

    @Test
    void playerRankingPassesParameters() throws Exception {
        mockMvc.perform(get("/api/rankings/players").param("minMatches", "5").param("limit", "50"))
                .andExpect(status().isOk());
        verify(ratingQueryService).findPlayerRanking(5, 50);
    }

    @Test
    void invalidRankingParametersAreRejected() throws Exception {
        mockMvc.perform(get("/api/rankings/players").param("minMatches", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/rankings/players").param("limit", "500"))
                .andExpect(status().isBadRequest());
        verify(ratingQueryService, never()).findPlayerRanking(anyInt(), anyInt());
    }

    @Test
    void teamRanking() throws Exception {
        when(ratingQueryService.findTeamRanking()).thenReturn(List.of(
                new TeamRanking(29, "Spain", 8, new BigDecimal("8.90"), 22, 14, 1, -6)));

        mockMvc.perform(get("/api/rankings/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].team").value("Spain"))
                .andExpect(jsonPath("$[0].points").value(22))
                .andExpect(jsonPath("$[0].fairPlayPoints").value(-6));
    }
}
