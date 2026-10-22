package com.emocio.analysefoot.analyse;

import com.emocio.analysefoot.dto.AnalyseAnswer;
import com.emocio.analysefoot.dto.AnalyseInterpretation;
import com.emocio.analysefoot.tools.StatMetrics;
import com.emocio.analysefoot.tools.TextNormalizer;
import com.emocio.analysefoot.dto.AnalyseQuestion;
import com.emocio.analysefoot.dto.PlayerReport;
import com.emocio.analysefoot.dto.RankedEntry;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Répond à la recherche de l'onglet Analyse. Le nom d'un joueur ou d'une équipe donne son rapport ; toute autre question
 * est confiée en priorité à l'assistant LLM, qui cherche lui-même avec les tools de lecture (classements, statistiques,
 * rapports, matchs, calculs) et dont la réponse courte est affichée telle quelle. Sans assistant, ou s'il n'aboutit pas,
 * les questions suggérées du catalogue sont proposées. Les classements SQL utilisent {@code RANK()} sur les vues du
 * tournoi de la dernière compétition importée : les ex aequo partagent un rang, le top 5 peut compter plus de 5 lignes.
 */
@Service
public class AnalyseService {

    static final int TOP = 5;
    static final int MAX_FOUND = 10;

    private final JdbcClient jdbcClient;
    private final QuestionInterpreter questionInterpreter;

    public AnalyseService(JdbcClient jdbcClient, QuestionInterpreter questionInterpreter) {
        this.jdbcClient = jdbcClient;
        this.questionInterpreter = questionInterpreter;
    }

    /** Questions suggérées (clic) ; elles ne servent plus à interpréter une saisie. */
    public List<AnalyseQuestion> questions() {
        return Arrays.stream(AnalyseCriterion.values()).map(AnalyseCriterion::toQuestion).toList();
    }

    public boolean assistantEnabled() {
        return questionInterpreter.enabled();
    }

    /** Question libre : nom de joueur ou d'équipe, sinon assistant LLM, sinon suggestions. */
    public AnalyseAnswer answer(String question) {
        boolean owner = Trace.begin();
        try {
            Trace.in("APP", "Question reçue", question);
            AnalyseAnswer answer = answerFree(question);
            return owner ? answer.withTrace(Trace.steps()) : answer;
        } finally {
            if (owner) {
                Trace.end();
            }
        }
    }

    private AnalyseAnswer answerFree(String question) {
        List<RankedEntry> found = findEntities(question);
        Trace.out("SQL", "Recherche d'un joueur ou d'une équipe par son nom", found.size() + " correspondance(s)");
        if (!found.isEmpty()) {
            return new AnalyseAnswer(found.size() == 1 ? "FOUND" : "CHOOSE", null, null, false, List.of(), found,
                    List.of(), new AnalyseInterpretation("NAME", null));
        }
        if (!questionInterpreter.enabled()) {
            Trace.out("APP", "LLM non consulté", "assistant désactivé (aucune clé configurée)");
            return notRecognized();
        }
        Trace.out("APP", "Question confiée à l'assistant", "le LLM cherche avec les tools de lecture");
        String text;
        try {
            text = questionInterpreter.answerFreely(question);
        } catch (RuntimeException e) {
            Trace.in("LLM", "Erreur de l'assistant", e.getClass().getSimpleName());
            return notRecognized();
        }
        return text == null || text.isBlank() ? notRecognized()
                : AnalyseAnswer.freeText(text.strip(), new AnalyseInterpretation("ASSISTANT", null));
    }

    private AnalyseAnswer notRecognized() {
        return new AnalyseAnswer("NOT_RECOGNIZED", null, null, false, List.of(), List.of(), questions(),
                new AnalyseInterpretation("NAME", null));
    }

    /**
     * Recherche par nom : un joueur (nom et/ou prénom, dans n'importe quel ordre, accents et casse ignorés) ou une
     * équipe (appellation). Chaque mot saisi doit commencer un mot du nom. Un nom complet exact l'emporte sur les
     * correspondances partielles ; au plus {@value #MAX_FOUND} résultats.
     */
    public List<RankedEntry> findEntities(String question) {
        String query = TextNormalizer.normalize(question);
        if (query.length() < 2) {
            return List.of();
        }
        List<String> words = Arrays.asList(query.split(" "));
        List<RankedEntry> teams = namesMatching(AnalyseCriterion.Scope.TEAM, query, words);
        List<RankedEntry> players = namesMatching(AnalyseCriterion.Scope.PLAYER, query, words);
        List<RankedEntry> found = new java.util.ArrayList<>(teams);
        found.addAll(players);
        List<RankedEntry> exact = found.stream()
                .filter(e -> TextNormalizer.normalize(e.name()).equals(query)).toList();
        List<RankedEntry> result = exact.isEmpty() ? found : exact;
        return result.size() > MAX_FOUND ? result.subList(0, MAX_FOUND) : result;
    }

    private List<RankedEntry> namesMatching(AnalyseCriterion.Scope scope, String query, List<String> words) {
        return entityNames(scope).stream()
                .filter(e -> {
                    List<String> nameWords = Arrays.asList(TextNormalizer.normalize(e.name()).split(" "));
                    return words.stream().allMatch(w -> nameWords.stream().anyMatch(n -> n.equals(w)
                            || (w.length() >= 3 && n.startsWith(w))));
                })
                .toList();
    }

    /** Tous les joueurs ou toutes les équipes de la dernière compétition importée. */
    List<RankedEntry> entityNames(AnalyseCriterion.Scope scope) {
        return jdbcClient.sql("SELECT DISTINCT %s AS id, %s AS name, %s AS team FROM %s WHERE competition_id = (SELECT MAX(id) FROM competition) ORDER BY name"
                        .formatted(scope.idColumn, scope.nameColumn, scope.teamColumn, scope.view))
                .query((rs, rowNum) -> new RankedEntry(1, scope.name(), rs.getInt("id"), rs.getString("name"),
                        rs.getString("team"), null, "", null, null))
                .list();
    }

    /**
     * Classement générique d'une statistique de joueur ou d'équipe hors catalogue (choisie par l'assistant du onglet ou
     * par un client MCP) : {@code RANK()} en SQL sur la vue du tournoi, ex aequo conservés, top 5. Les valeurs
     * inconnues (statistique absente) sont écartées.
     */
    public AnalyseAnswer answerGeneric(String scope, String metric, String order) {
        boolean owner = Trace.begin();
        try {
            AnalyseAnswer answer = answerGeneric(scope, metric, order, (AnalyseInterpretation) null);
            return owner ? answer.withTrace(Trace.steps()) : answer;
        } finally {
            if (owner) {
                Trace.end();
            }
        }
    }

    private AnalyseAnswer answerGeneric(String scope, String metric, String order, AnalyseInterpretation interpretation) {
        boolean players = StatMetrics.isPlayerScope(scope);
        String expression = players || "TEAM".equalsIgnoreCase(scope) ? StatMetrics.expression(players, metric) : null;
        if (expression == null) {
            throw new IllegalArgumentException("Statistique inconnue : " + metric + " (" + scope + ").");
        }
        String direction = "ASC".equalsIgnoreCase(order) ? "ASC" : "DESC";
        AnalyseCriterion.Scope s = players ? AnalyseCriterion.Scope.PLAYER : AnalyseCriterion.Scope.TEAM;
        String unit = StatMetrics.label(players, metric);
        // expression, vue et colonnes viennent de listes fermées : jamais d'une saisie
        List<RankedEntry> top = jdbcClient.sql("""
                SELECT * FROM (
                    SELECT %s AS id, %s AS name, %s AS team, CAST(%s AS NUMERIC) AS value,
                           RANK() OVER (ORDER BY %s %s) AS rank
                    FROM %s
                    WHERE competition_id = (SELECT MAX(id) FROM competition) AND %s IS NOT NULL
                ) ranked
                WHERE rank <= :top
                ORDER BY rank, name
                """.formatted(s.idColumn, s.nameColumn, s.teamColumn, expression, expression, direction, s.view, expression))
                .param("top", TOP)
                .query((rs, rowNum) -> new RankedEntry(rs.getInt("rank"), s.name(), rs.getInt("id"), rs.getString("name"),
                        rs.getString("team"), rs.getBigDecimal("value"), unit, null, null))
                .list();
        Trace.out("SQL", "Classement RANK() générique : " + metric + " " + direction + " (" + s.name() + ")",
                top.size() + " ligne(s)" + (top.isEmpty() ? "" : ", rang 1 : " + top.getFirst().name()));
        boolean fictitious = StatMetrics.isFictitious(metric);
        AnalyseQuestion question = new AnalyseQuestion("GENERIC:" + s.name() + ":" + metric + ":" + direction,
                genericLabel(s.name(), metric, direction), s.name(), fictitious);
        return new AnalyseAnswer(top.isEmpty() ? "NO_DATA" : "ANSWERED", question,
                "Classement par " + unit + ", " + ("ASC".equals(direction) ? "du plus petit au plus grand" : "du plus grand au plus petit")
                        + " ; les ex aequo partagent le rang.",
                fictitious, top.stream().filter(e -> e.rank() == 1).toList(), top, List.of(), interpretation);
    }

    private static String genericLabel(String scope, String metric, String order) {
        boolean players = StatMetrics.isPlayerScope(scope);
        return (players ? "Joueurs classés" : "Équipes classées") + " par " + StatMetrics.label(players, metric)
                + ("ASC".equals(order) ? " (croissant)" : "");
    }

    /** Suggestion cliquée : réponse directe, sans interprétation. */
    public AnalyseAnswer answer(AnalyseCriterion criterion) {
        boolean owner = Trace.begin();
        try {
            AnalyseAnswer answer = answer(criterion, null);
            return owner ? answer.withTrace(Trace.steps()) : answer;
        } finally {
            if (owner) {
                Trace.end();
            }
        }
    }

    private AnalyseAnswer answer(AnalyseCriterion criterion, AnalyseInterpretation interpretation) {
        List<RankedEntry> top = ranking(criterion);
        Trace.out("SQL", "Classement RANK() de " + criterion.name(), top.size() + " ligne(s)"
                + (top.isEmpty() ? "" : ", rang 1 : " + top.getFirst().name()));
        return new AnalyseAnswer(top.isEmpty() ? "NO_DATA" : "ANSWERED", criterion.toQuestion(), criterion.explanation(),
                criterion.fictitious(), top.stream().filter(e -> e.rank() == 1).toList(), top, List.of(), interpretation);
    }

    /**
     * Rang d'un joueur sur chaque critère de joueur, parmi la population du critère ({@code outOf}).
     * Rang null : le joueur n'en fait pas partie (gardien pour le plus fair-play, aucun but pour le buteur...).
     */
    public List<PlayerReport.RankLine> playerRanks(int playerId) {
        return Arrays.stream(AnalyseCriterion.values())
                .filter(c -> c.scope() == AnalyseCriterion.Scope.PLAYER)
                .map(c -> jdbcClient.sql("""
                                WITH ranked AS (
                                    SELECT player_id AS id, RANK() OVER (ORDER BY %s) AS rank
                                    FROM v_player_tournament_stats
                                    WHERE competition_id = (SELECT MAX(id) FROM competition) AND %s
                                )
                                SELECT (SELECT rank FROM ranked WHERE id = :id) AS rank, (SELECT COUNT(*) FROM ranked) AS out_of
                                """.formatted(c.order, c.filter))
                        .param("id", playerId)
                        .query((rs, rowNum) -> {
                            int rank = rs.getInt("rank");
                            return new PlayerReport.RankLine(c.name(), c.label(), rs.wasNull() ? null : rank,
                                    rs.getInt("out_of"));
                        })
                        .single())
                .toList();
    }

    private List<RankedEntry> ranking(AnalyseCriterion criterion) {
        AnalyseCriterion.Scope scope = criterion.scope();
        String sql = """
                SELECT * FROM (
                    SELECT %s AS id, %s AS name, %s AS team, %s AS value, %s AS tie_break_value, %s AS value_label,
                           RANK() OVER (ORDER BY %s) AS rank
                    FROM %s
                    WHERE competition_id = (SELECT MAX(id) FROM competition) AND %s
                ) ranked
                WHERE rank <= :top
                ORDER BY rank, name
                """.formatted(scope.idColumn, scope.nameColumn, scope.teamColumn, criterion.value,
                criterion.tieBreak == null ? "NULL" : criterion.tieBreak,
                criterion.valueLabel == null ? "NULL" : criterion.valueLabel,
                criterion.order, scope.view, criterion.filter);
        return jdbcClient.sql(sql)
                .param("top", TOP)
                .query((rs, rowNum) -> new RankedEntry(rs.getInt("rank"), scope.name(), rs.getInt("id"),
                        rs.getString("name"), rs.getString("team"), rs.getBigDecimal("value"), criterion.unit(),
                        rs.getBigDecimal("tie_break_value"), rs.getString("value_label")))
                .list();
    }
}
