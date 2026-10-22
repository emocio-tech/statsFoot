package com.emocio.analysefoot.tools;

import com.emocio.analysefoot.dto.MatchSummary;
import com.emocio.analysefoot.service.RatingQueryService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tools de lecture du mode libre : {@code stat} lit n'importe quelle statistique de joueur ou d'équipe des vues du
 * tournoi (liste, somme, moyenne, maximum, minimum, effectif) et {@code calculate} fait l'arithmétique exactement. Le
 * modèle les enchaîne avec les tools du serveur MCP ({@code find}, {@code player_report}...). Aucun SQL libre : la
 * statistique est choisie dans une liste fermée, jamais saisie dans la requête.
 */
@Configuration(proxyBeanMethods = false)
public class StatTools {

    static final int DEFAULT_LIMIT = 5;
    static final int MAX_LIMIT = 20;

    static final List<String> STAGES = List.of("GROUP", "ROUND_OF_32", "ROUND_OF_16", "QUARTER_FINAL", "SEMI_FINAL",
            "THIRD_PLACE", "FINAL");
    static final int MAX_MATCHES = 104;

    static final List<String> AGGREGATES = List.of("NONE", "SUM", "AVG", "MAX", "MIN", "COUNT");

    private static final String COMPETITION = "competition_id = (SELECT MAX(id) FROM competition)";

    private static final int MATCHES_DEFAULT_LIMIT = 20;

    private final JdbcClient jdbcClient;
    private final RatingQueryService ratingQueryService;

    public StatTools(JdbcClient jdbcClient, RatingQueryService ratingQueryService) {
        this.jdbcClient = jdbcClient;
        this.ratingQueryService = ratingQueryService;
    }

    @Bean
    ToolCallbackProvider statToolsProvider() {
        return MethodToolCallbackProvider.builder().toolObjects(this).build();
    }

    /** Une ligne : joueur ou équipe (nom, équipe du joueur) et valeur de la statistique. */
    public record Row(String name, String team, BigDecimal value) {
    }

    public record StatResult(String scope, String metric, String aggregate, BigDecimal value, int considered,
                             List<Row> rows, boolean fictitious, String note) {
    }

    @Tool(description = "Lit une statistique de joueur ou d'équipe du tournoi (dernière compétition importée) et la liste, "
            + "la totalise ou la moyenne. Statistiques joueur : matches_played, matches_started, minutes_played, goals, "
            + "penalty_goals, own_goals, decisive_goals, assists, yellow_cards, red_cards, second_yellows, direct_reds, "
            + "fair_play_points, fouls_committed, free_kicks_won, free_kicks_conceded, duels_won, duels_lost, corners, "
            + "offsides, distance_km, man_of_the_match_count, rating, rating_weighted. Statistiques équipe : "
            + "matches_played, wins, draws, losses, points, goals_for, goals_against, goal_difference, yellow_cards, "
            + "red_cards, fair_play_points, fouls_committed, free_kicks_won, free_kicks_conceded, duels_won, duels_lost, "
            + "corners, offsides, distance_km, rating, final_position. aggregate : NONE (liste classée, limit lignes), SUM, "
            + "AVG, MAX, MIN ou COUNT sur les lignes retenues. name et team filtrent (accents et casse ignorés ; team "
            + "ne vaut que pour un joueur). Le résultat dit si la donnée est fictive. Pour tout calcul entre plusieurs "
            + "résultats, utiliser l'outil calculate.")
    public StatResult stat(
            @ToolParam(description = "PLAYER ou TEAM") String scope,
            @ToolParam(description = "Nom de la statistique, voir la liste de l'outil (ex. goals, corners, goal_difference)")
            String metric,
            @ToolParam(description = "NONE, SUM, AVG, MAX, MIN ou COUNT", required = false) String aggregate,
            @ToolParam(description = "Nom (ou début de nom) du joueur ou de l'équipe, pour ne garder que lui", required = false)
            String name,
            @ToolParam(description = "Équipe du joueur, pour ne garder que son effectif (scope PLAYER)", required = false)
            String team,
            @ToolParam(description = "Tri de la liste : DESC (défaut, du plus grand) ou ASC", required = false) String order,
            @ToolParam(description = "Nombre de lignes de la liste, 5 par défaut, 20 au plus", required = false) Integer limit) {
        boolean players = "PLAYER".equalsIgnoreCase(scope);
        if (!players && !"TEAM".equalsIgnoreCase(scope)) {
            throw new IllegalArgumentException("scope inconnu : " + scope + " (PLAYER ou TEAM).");
        }
        String key = metric == null ? "" : metric.trim().toLowerCase(Locale.ROOT);
        String expression = StatMetrics.expression(players, key);
        if (expression == null) {
            throw new IllegalArgumentException("Statistique inconnue : " + metric + ". Disponibles : "
                    + String.join(", ", StatMetrics.names(players)) + ".");
        }
        String agg = aggregate == null || aggregate.isBlank() ? "NONE" : aggregate.trim().toUpperCase(Locale.ROOT);
        if (!AGGREGATES.contains(agg)) {
            throw new IllegalArgumentException("aggregate inconnu : " + aggregate + " (" + String.join(", ", AGGREGATES) + ").");
        }
        // expression et colonnes viennent des listes fermées ci-dessus : jamais d'une saisie
        List<Row> all = players
                ? jdbcClient.sql("SELECT player AS name, team, CAST(" + expression + " AS NUMERIC) AS value FROM "
                        + "v_player_tournament_stats WHERE " + COMPETITION)
                        .query((rs, i) -> new Row(rs.getString("name"), rs.getString("team"), rs.getBigDecimal("value")))
                        .list()
                : jdbcClient.sql("SELECT team AS name, CAST(" + expression + " AS NUMERIC) AS value FROM "
                        + "v_team_tournament_stats WHERE " + COMPETITION)
                        .query((rs, i) -> new Row(rs.getString("name"), null, rs.getBigDecimal("value")))
                        .list();
        return compute(players ? "PLAYER" : "TEAM", key, agg, all, name, players ? team : null, order, limit);
    }

    @Tool(description = "Calcule exactement une expression arithmétique : nombres décimaux, + - * / %, parenthèses. "
            + "À utiliser pour toute somme, différence, moyenne, ratio ou pourcentage à partir de nombres obtenus des "
            + "autres outils, plutôt que de calculer de tête.")
    public String calculate(@ToolParam(description = "Ex. (14 + 9) / 2 ou 100 * 7 / 8") String expression) {
        return Calculator.evaluate(expression).toPlainString();
    }

    @Tool(description = "Liste les matchs du tournoi, par ordre chronologique : date, phase (GROUP, ROUND_OF_32, ROUND_OF_16, "
            + "QUARTER_FINAL, SEMI_FINAL, THIRD_PLACE, FINAL), groupe, stade et ville, équipes, score final (prolongation "
            + "comprise, extraTime vrai si elle a eu lieu), tirs au but et note de chaque équipe. team ne garde que les "
            + "matchs de cette équipe, stage que ceux de cette phase (accents et casse ignorés pour team) ; limit : "
            + "20 par défaut, 104 au plus. Sert aux questions sur un score, un vainqueur, un stade ou un calendrier.")
    public List<MatchSummary> matches(
            @ToolParam(description = "Nom (ou début de nom) d'une des deux équipes", required = false) String team,
            @ToolParam(description = "Phase : GROUP, ROUND_OF_32, ROUND_OF_16, QUARTER_FINAL, SEMI_FINAL, THIRD_PLACE ou FINAL",
                    required = false) String stage,
            @ToolParam(description = "Nombre de matchs, 20 par défaut, 104 au plus", required = false) Integer limit) {
        String wanted = stage == null || stage.isBlank() ? null : stage.trim().toUpperCase(Locale.ROOT);
        if (wanted != null && !STAGES.contains(wanted)) {
            throw new IllegalArgumentException("Phase inconnue : " + stage + " (" + String.join(", ", STAGES) + ").");
        }
        int max = limit == null || limit < 1 ? MATCHES_DEFAULT_LIMIT : Math.min(limit, MAX_MATCHES);
        return ratingQueryService.findMatches().stream()
                .filter(m -> wanted == null || wanted.equals(m.stage()))
                .filter(m -> team == null || team.isBlank() || matches(m.team1(), team) || matches(m.team2(), team))
                .limit(max)
                .toList();
    }

    /** Filtre, trie et agrège des lignes déjà lues ; sans accès à la base, pour être testé seul. */
    static StatResult compute(String scope, String metric, String aggregate, List<Row> all, String name, String team,
                              String order, Integer limit) {
        List<Row> rows = new ArrayList<>(all.stream()
                .filter(r -> matches(r.name(), name))
                .filter(r -> matches(r.team(), team))
                .toList());
        int considered = rows.size();
        boolean fictitious = StatMetrics.isFictitious(metric);
        String note = fictitious ? "données fictives" : null;
        if ("NONE".equals(aggregate)) {
            // valeurs inconnues toujours en fin de liste, quel que soit le sens du tri
            Comparator<BigDecimal> direction = "ASC".equalsIgnoreCase(order) ? Comparator.naturalOrder()
                    : Comparator.reverseOrder();
            rows.sort(Comparator.comparing(Row::value, Comparator.nullsLast(direction)));
            int max = limit == null || limit < 1 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
            return new StatResult(scope, metric, aggregate, null, considered,
                    List.copyOf(rows.size() > max ? rows.subList(0, max) : rows), fictitious, note);
        }
        List<BigDecimal> values = rows.stream().map(Row::value).filter(v -> v != null).toList();
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal result = switch (aggregate) {
            case "COUNT" -> BigDecimal.valueOf(values.size());
            case "SUM" -> sum;
            case "AVG" -> values.isEmpty() ? null
                    : sum.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP).stripTrailingZeros();
            case "MAX" -> values.stream().max(Comparator.naturalOrder()).orElse(null);
            default -> values.stream().min(Comparator.naturalOrder()).orElse(null);
        };
        if (values.isEmpty() && !"COUNT".equals(aggregate)) {
            note = (note == null ? "" : note + " ; ") + "aucune valeur sur les lignes retenues";
        }
        return new StatResult(scope, metric, aggregate, result, considered, List.of(), fictitious, note);
    }

    /** Filtre vide : tout passe ; sinon chaque mot saisi doit commencer un mot du nom (accents et casse ignorés). */
    private static boolean matches(String value, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        if (value == null) {
            return false;
        }
        List<String> valueWords = Arrays.asList(TextNormalizer.normalize(value).split(" "));
        return Arrays.stream(TextNormalizer.normalize(filter).split(" "))
                .allMatch(w -> valueWords.stream().anyMatch(v -> v.equals(w) || (w.length() >= 2 && v.startsWith(w))));
    }
}
