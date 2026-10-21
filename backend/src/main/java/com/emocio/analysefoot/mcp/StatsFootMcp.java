package com.emocio.analysefoot.mcp;

import com.emocio.analysefoot.analyse.AnalyseCriterion;
import com.emocio.analysefoot.analyse.AnalyseService;
import com.emocio.analysefoot.dto.AnalyseAnswer;
import com.emocio.analysefoot.dto.AnalyseQuestion;
import com.emocio.analysefoot.dto.PlayerReport;
import com.emocio.analysefoot.dto.RankedEntry;
import com.emocio.analysefoot.dto.TeamReport;
import com.emocio.analysefoot.service.ReportQueryService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * Serveur MCP « statsfoot » (lecture seule) : les mêmes fonctions que l'API, adossées au même catalogue et aux mêmes
 * vues SQL. Les chiffres viennent de la base ; rien n'est calculé ni écrit ici.
 */
@Configuration(proxyBeanMethods = false)
public class StatsFootMcp {

    private final AnalyseService analyseService;
    private final ReportQueryService reportQueryService;

    public StatsFootMcp(AnalyseService analyseService, ReportQueryService reportQueryService) {
        this.analyseService = analyseService;
        this.reportQueryService = reportQueryService;
    }

    @Bean
    ToolCallbackProvider statsFootTools() {
        return MethodToolCallbackProvider.builder().toolObjects(this).build();
    }

    @Tool(description = "Liste les questions du catalogue de l'onglet Analyse (identifiant, libellé, portée joueur ou équipe, "
            + "donnée fictive ou non). Les identifiants servent à l'outil rank.")
    public List<AnalyseQuestion> questions() {
        return analyseService.questions();
    }

    @Tool(description = "Classement (top 5, ex aequo gardés) d'une question du catalogue, avec le ou les vainqueurs, la "
            + "règle appliquée et l'indication de données fictives.")
    public AnalyseAnswer rank(@ToolParam(description = "Identifiant d'une question, voir l'outil questions (ex. TOP_SCORER)")
                              String criterion) {
        AnalyseCriterion found = Arrays.stream(AnalyseCriterion.values())
                .filter(c -> c.name().equalsIgnoreCase(criterion)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Question inconnue : " + criterion
                        + ". Voir l'outil questions."));
        return analyseService.answer(found);
    }

    @Tool(description = "Recherche un joueur (nom et/ou prénom) ou une équipe (appellation) de la dernière compétition "
            + "importée et renvoie leurs identifiants, à passer à player_report ou team_report.")
    public List<RankedEntry> find(@ToolParam(description = "Nom, prénom ou appellation, accents et casse ignorés") String name) {
        return analyseService.findEntities(name);
    }

    @Tool(description = "Rapport complet d'un joueur : totaux du tournoi, matchs, meilleur match, rangs sur les critères ; "
            + "fictiveFields liste les champs fictifs.")
    public PlayerReport player_report(@ToolParam(description = "Identifiant du joueur, voir l'outil find") int playerId) {
        return reportQueryService.playerReport(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Joueur inconnu : " + playerId));
    }

    @Tool(description = "Rapport complet d'une équipe : totaux du tournoi, matchs, effectif, meilleur buteur et meilleur joueur ; "
            + "fictitiousFields liste les champs fictifs.")
    public TeamReport team_report(@ToolParam(description = "Identifiant de l'équipe, voir l'outil find") int teamId) {
        return reportQueryService.teamReport(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Équipe inconnue : " + teamId));
    }
}
