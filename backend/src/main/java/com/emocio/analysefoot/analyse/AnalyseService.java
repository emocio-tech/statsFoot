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
        Interpretation interpretation = resolve(question);
        return switch (interpretation.outcome()) {
            case RECOGNIZED -> answer(interpretation.criterion(), new AnalyseInterpretation(
                    interpretation.source().name(), interpretation.criterion().label()));
            case AMBIGUOUS -> new AnalyseAnswer("AMBIGUOUS", null, null, false, List.of(), List.of(),
                    interpretation.alternatives().stream().map(AnalyseCriterion::toQuestion).toList(),
                    new AnalyseInterpretation(interpretation.source().name(), null));
            case NONE -> new AnalyseAnswer("NOT_RECOGNIZED", null, null, false, List.of(), List.of(), questions(),
                    new AnalyseInterpretation(interpretation.source().name(), null));
        };
    }

    /**
     * Interprète une question libre. L'assistant n'est consulté que si les mots-clés ne la reconnaissent pas ou la
     * jugent ambiguë, s'il est configuré et sous le plafond ; s'il ne tranche pas ou échoue, les mots-clés s'appliquent.
     */
    Interpretation resolve(String question) {
        QuestionMatcher.Match match = questionMatcher.match(question);
        if (match.recognized()) {
            return Interpretation.recognized(match.candidates().getFirst(), Interpretation.Source.KEYWORDS);
        }
        Interpretation keywords = match.ambiguous()
                ? Interpretation.ambiguous(match.candidates(), Interpretation.Source.KEYWORDS)
                : Interpretation.none(Interpretation.Source.KEYWORDS);
        if (!questionInterpreter.enabled() || !rateLimiter.tryAcquire()) {
            return keywords;
        }
        Interpretation assistant;
        try {
            assistant = questionInterpreter.interpret(question);
        } catch (RuntimeException e) {
            return keywords;
        }
        return assistant == null || assistant.outcome() == Interpretation.Outcome.NONE ? keywords : assistant;
    }

    /** Suggestion cliquée : réponse directe, sans interprétation. */
    public AnalyseAnswer answer(AnalyseCriterion criterion) {
        return answer(criterion, null);
    }

    private AnalyseAnswer answer(AnalyseCriterion criterion, AnalyseInterpretation interpretation) {
        List<RankedEntry> top = ranking(criterion);
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
