package com.emocio.analysefoot.controller;

import com.emocio.analysefoot.dto.PlayerReport;
import com.emocio.analysefoot.dto.TeamReport;
import com.emocio.analysefoot.service.ReportQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportQueryService reportQueryService;

    @Test
    void playerReport() throws Exception {
        PlayerReport.Totals totals = new PlayerReport.Totals(8, 8, 698, 10, 1, 0, 3, 6, 1, 0, 0, 0, -1,
                9, 18, 9, 80, 70, 7, 6, new BigDecimal("80.40"), 5, new BigDecimal("8.1"), new BigDecimal("8.1"), 0, 0);
        when(reportQueryService.playerReport(664)).thenReturn(Optional.of(new PlayerReport(664, "Kylian MBAPPE", 33,
                "France", false, totals, List.of(), null,
                List.of(new PlayerReport.RankLine("TOP_SCORER", "Meilleur buteur", 1, 140)),
                PlayerReport.FICTITIOUS_FIELDS)));

        mockMvc.perform(get("/api/players/664/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kylian MBAPPE"))
                .andExpect(jsonPath("$.totals.goals").value(10))
                .andExpect(jsonPath("$.ranks[0].rank").value(1))
                .andExpect(jsonPath("$.fictitiousFields[0]").value("assists"));
    }

    @Test
    void teamReport() throws Exception {
        TeamReport.Totals totals = new TeamReport.Totals(8, 7, 1, 0, 22, 14, 1, 6, 0, -6, 88, 115, 96, 885, 749,
                37, 14, new BigDecimal("937.64"), new BigDecimal("8.9"), "FINAL", 1);
        when(reportQueryService.teamReport(29)).thenReturn(Optional.of(new TeamReport(29, "Spain", totals,
                List.of(), List.of(), null, null, TeamReport.FICTITIOUS_FIELDS)));

        mockMvc.perform(get("/api/teams/29/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team").value("Spain"))
                .andExpect(jsonPath("$.totals.finalPosition").value(1))
                .andExpect(jsonPath("$.fictitiousFields").isArray());
    }

    @Test
    void unknownIdsAreNotFound() throws Exception {
        when(reportQueryService.playerReport(-1)).thenReturn(Optional.empty());
        when(reportQueryService.teamReport(-1)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/players/-1/report")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/teams/-1/report")).andExpect(status().isNotFound());
    }
}
