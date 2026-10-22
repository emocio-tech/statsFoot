package com.emocio.analysefoot.analyse;

import com.emocio.analysefoot.dto.AnalyseAnswer;
import com.emocio.analysefoot.dto.AnalyseInterpretation;
import com.emocio.analysefoot.dto.AnalyseQuestion;
import com.emocio.analysefoot.dto.PlayerReport;
import com.emocio.analysefoot.dto.RankedEntry;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Répond aux questions du catalogue : classement SQL avec {@code RANK()} sur les vues du tournoi de la dernière
 * compétition importée. Les ex aequo partagent un rang ; le top 5 peut donc compter plus de 5 lignes.
 */
@Service
public class AnalyseService {

    static final int TOP = 5;
    static final int MAX_FOUND = 10;

    private final JdbcClient jdbcClient;
    private final QuestionMatcher questionMatcher;
    private final QuestionInterpreter questionInterpreter;
    private final AssistantRateLimiter rateLimiter;

    public AnalyseService(JdbcClient jdbcClient, QuestionMatcher questionMatcher, QuestionInterpreter questionInterpreter,
                          AssistantRateLimiter rateLimiter) {
        this.jdbcClient = jdbcClient;
        this.questionMatcher = questionMatcher;
        this.questionInterpreter = questionInterpreter;
        this.rateLimiter = rateLimiter;
    }

    public List<AnalyseQuestion> questions() {
        return Arrays.stream(AnalyseCriterion.values()).map(AnalyseCriterion::toQuestion).toList();
    }

    public boolean assistantEnabled() {
        return questionInterpreter.enabled();
    }

    /** Question libre : mots-clés d'abord, assistant (tool Claude rank) seulement s'ils échouent, puis réponse. */
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
        QuestionMatcher.Match match = questionMatcher.match(question);
        if (!match.recognized() && !match.ambiguous()) {
            List<RankedEntry> found = findEntities(question);
            Trace.out("SQL", "Recherche d'un joueur ou d'une équipe par son nom", found.size() + " correspondance(s)");
            if (!found.isEmpty()) {
                return new AnalyseAnswer(found.size() == 1 ? "FOUND" : "CHOOSE", null, null, false, List.of(), found,
                        List.of(), new AnalyseInterpretation("KEYWORDS", null));
            }
        }
        Interpretation interpretation = resolve(question);
        return switch (interpretation.outcome()) {
            case RECOGNIZED -> answer(interpretation.criterion(), new AnalyseInterpretation(
                    interpretation.source().name(), interpretation.criterion().label()));
            case AMBIGUOUS -> new AnalyseAnswer("AMBIGUOUS", null, null, false, List.of(), List.of(),
                    interpretation.alternatives().stream().map(AnalyseCriterion::toQuestion).toList(),
                    new AnalyseInterpretation(interpretation.source().name(), null));
            case NONE -> freeAnswer(question, interpretation).orElseGet(() -> new AnalyseAnswer("NOT_RECOGNIZED", null,
                    null, false, List.of(), List.of(), questions(),
                    new AnalyseInterpretation(interpretation.source().name(), null)));
        };
    }

    /**
     * Mode libre : l'assistant a jugé la question sans rapport avec le catalogue ; il la traite lui-même avec les tools
     * de lecture et sa réponse courte est affichée telle quelle. Vide si le mode n'aboutit pas (plafond d'appels,
     * erreur, aucune réponse) : la question reste alors « non reconnue ».
     */
    private java.util.Optional<AnalyseAnswer> freeAnswer(String question, Interpretation interpretation) {
        if (!interpretation.unrelated()) {
            return java.util.Optional.empty();
        }
        if (!rateLimiter.tryAcquire()) {
            Trace.out("APP", "Mode libre non lancé", "plafond d'appels par minute atteint");
            return java.util.Optional.empty();
        }
        Trace.out("APP", "Question hors catalogue : mode libre", "le LLM cherche avec les tools de lecture");
        String text;
        try {
            text = questionInterpreter.answerFreely(question);
        } catch (RuntimeException e) {
            Trace.in("LLM", "Erreur du mode libre", e.getClass().getSimpleName());
            return java.util.Optional.empty();
        }
        return text == null || text.isBlank() ? java.util.Optional.empty()
                : java.util.Optional.of(AnalyseAnswer.freeText(text.strip(), new AnalyseInterpretation("ASSISTANT", null)));
    }

    /**
     * Recherche par nom : un joueur (nom et/ou prénom, dans n'importe quel ordre, accents et casse ignorés) ou une
     * équipe (appellation). Chaque mot saisi doit commencer un mot du nom. Un nom complet exact l'emporte sur les
     * correspondances partielles ; au plus {@value #MAX_FOUND} résultats.
     */
    public List<RankedEntry> findEntities(String question) {
        String query = QuestionMatcher.normalize(question);
        if (query.length() < 2) {
            return List.of();
        }
        List<String> words = Arrays.asList(query.split(" "));
        List<RankedEntry> teams = namesMatching(AnalyseCriterion.Scope.TEAM, query, words);
        List<RankedEntry> players = namesMatching(AnalyseCriterion.Scope.PLAYER, query, words);
        List<RankedEntry> found = new java.util.ArrayList<>(teams);
        found.addAll(players);
        List<RankedEntry> exact = found.stream()
                .filter(e -> QuestionMatcher.normalize(e.name()).equals(query)).toList();
        List<RankedEntry> result = exact.isEmpty() ? found : exact;
        return result.size() > MAX_FOUND ? result.subList(0, MAX_FOUND) : result;
    }

    private List<RankedEntry> namesMatching(AnalyseCriterion.Scope scope, String query, List<String> words) {
        return entityNames(scope).stream()
                .filter(e -> {
                    List<String> nameWords = Arrays.asList(QuestionMatcher.normalize(e.name()).split(" "));
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
     * Interprète une question libre. L'assistant n'est consulté que si les mots-clés ne la reconnaissent pas ou la
     * jugent ambiguë, s'il est configuré et sous le plafond ; s'il ne tranche pas ou échoue, les mots-clés s'appliquent.
     */
    Interpretation resolve(String question) {
        QuestionMatcher.Match match = questionMatcher.match(question);
        Trace.out("APP", "Reconnaissance par mots-clés", match.recognized() ? "reconnue : " + match.candidates().getFirst()
                : match.ambiguous() ? "ambiguë : " + match.candidates() : "non reconnue");
        if (match.recognized()) {
            return Interpretation.recognized(match.candidates().getFirst(), Interpretation.Source.KEYWORDS);
        }
        Interpretation keywords = match.ambiguous()
                ? Interpretation.ambiguous(match.candidates(), Interpretation.Source.KEYWORDS)
                : Interpretation.none(Interpretation.Source.KEYWORDS);
        if (!questionInterpreter.enabled()) {
            Trace.out("APP", "LLM non consulté", "assistant désactivé (aucune clé configurée)");
            return keywords;
        }
        if (!rateLimiter.tryAcquire()) {
            Trace.out("APP", "LLM non consulté", "plafond d'appels par minute atteint");
            return keywords;
        }
        Interpretation assistant;
        try {
            assistant = questionInterpreter.interpret(question);
        } catch (RuntimeException e) {
            Trace.in("LLM", "Erreur de l'assistant", e.getClass().getSimpleName());
            return keywords;
        }
        if (assistant != null && assistant.unrelated() && keywords.outcome() == Interpretation.Outcome.NONE) {
            return keywords.asUnrelated();
        }
        return assistant == null || assistant.outcome() == Interpretation.Outcome.NONE ? keywords : assistant;
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
