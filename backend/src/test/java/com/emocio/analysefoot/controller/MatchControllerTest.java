package com.emocio.analysefoot.controller;

import com.emocio.analysefoot.dto.MatchRatings;
import com.emocio.analysefoot.dto.MatchSummary;
import com.emocio.analysefoot.dto.PlayerRating;
import com.emocio.analysefoot.dto.TeamRating;
import com.emocio.analysefoot.service.RatingQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MatchController.class)
class MatchControllerTest {

    private static final MatchSummary FINAL = new MatchSummary(104, "FINAL", null,
            OffsetDateTime.of(2026, 7, 19, 19, 0, 0, 0, ZoneOffset.UTC), "Stadium", "City",
            1, "Spain", 2, "Argentina", 1, 0, true, null, null, new BigDecimal("9.4"), new BigDecimal("2.6"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RatingQueryService ratingQueryService;

    @Test
    void listsMatchesWithTeamRatings() throws Exception {
        when(ratingQueryService.findMatches()).thenReturn(List.of(FINAL));

        mockMvc.perform(get("/api/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(104))
                .andExpect(jsonPath("$[0].stage").value("FINAL"))
                .andExpect(jsonPath("$[0].kickoffAt").value("2026-07-19T19:00:00Z"))
                .andExpect(jsonPath("$[0].rating1").value(9.4))
                .andExpect(jsonPath("$[0].penalties1").value(nullValue()));
    }

    @Test
    void returnsMatchRatings() throws Exception {
        TeamRating spain = new TeamRating(1, "Spain", new BigDecimal("9.4"), "W", 3, 1, 0, null, 0);
        PlayerRating player = new PlayerRating(10, 1, "Ferran TORRES", new BigDecimal("9.0"), false, false,
                58, 1, 0, 1, 0, false, 0, 0);
        when(ratingQueryService.findMatchRatings(104))
                .thenReturn(Optional.of(new MatchRatings(FINAL, List.of(spain), List.of(player))));

        mockMvc.perform(get("/api/matches/104/ratings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.match.team1").value("Spain"))
                .andExpect(jsonPath("$.teams[0].result").value("W"))
                .andExpect(jsonPath("$.players[0].name").value("Ferran TORRES"))
                .andExpect(jsonPath("$.players[0].rating").value(9.0))
                .andExpect(jsonPath("$.players[0].starter").value(false));
    }

    @Test
    void unknownMatchReturns404() throws Exception {
        when(ratingQueryService.findMatchRatings(999)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/matches/999/ratings"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonNumericMatchIdReturns400() throws Exception {
        mockMvc.perform(get("/api/matches/abc/ratings"))
                .andExpect(status().isBadRequest());
    }
}
