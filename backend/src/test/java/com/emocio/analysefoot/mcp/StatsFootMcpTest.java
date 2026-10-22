package com.emocio.analysefoot.mcp;

import com.emocio.analysefoot.analyse.AnalyseService;
import com.emocio.analysefoot.service.ReportQueryService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Le serveur MCP statsfoot : cinq outils en lecture seule, entrées invalides refusées. */
class StatsFootMcpTest {

    private final AnalyseService analyseService = mock(AnalyseService.class);
    private final ReportQueryService reportQueryService = mock(ReportQueryService.class);
    private final StatsFootMcp mcp = new StatsFootMcp(analyseService, reportQueryService);

    @Test
    void exposesTheFiveReadOnlyTools() {
        assertThat(Arrays.stream(mcp.statsFootTools().getToolCallbacks()).map(t -> t.getToolDefinition().name()))
                .containsExactlyInAnyOrder("metrics", "rank", "find", "player_report", "team_report");
    }

    @Test
    void unknownCriterionAndIdsAreRejected() {
        when(analyseService.answerGeneric("PLAYER", "salary", "DESC"))
                .thenThrow(new IllegalArgumentException("Statistique inconnue : salary"));
        when(reportQueryService.playerReport(1)).thenReturn(Optional.empty());
        when(reportQueryService.teamReport(1)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> mcp.rank("PLAYER", "salary", null)).hasMessageContaining("Statistique inconnue");
                assertThatThrownBy(() -> mcp.player_report(1)).hasMessageContaining("Joueur inconnu");
        assertThatThrownBy(() -> mcp.team_report(1)).hasMessageContaining("Équipe inconnue");
    }
}
