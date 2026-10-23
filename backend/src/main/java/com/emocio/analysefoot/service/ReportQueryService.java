package com.emocio.analysefoot.service;

import com.emocio.analysefoot.analyse.AnalyseService;
import com.emocio.analysefoot.dto.PlayerReport;
import com.emocio.analysefoot.dto.TeamReport;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Rapports complets d'un joueur et d'une équipe, lus dans les vues du tournoi et les tables de notes.
 * Les rangs du joueur viennent du catalogue des critères de l'onglet Analyse.
 */
@Service
public class ReportQueryService {

    private static final String LAST_COMPETITION = "(SELECT MAX(id) FROM competition)";

    private record Identity(Integer playerId, String name, Integer teamId, String team, Boolean goalkeeper) {
    }

    private final JdbcClient jdbcClient;
    private final AnalyseService analyseService;

    public ReportQueryService(JdbcClient jdbcClient, AnalyseService analyseService) {
        this.jdbcClient = jdbcClient;
        this.analyseService = analyseService;
    }

    public Optional<PlayerReport> playerReport(int playerId) {
        Optional<Identity> identity = jdbcClient.sql("""
                        SELECT player_id, player AS name, team_id, team, is_goalkeeper AS goalkeeper
                        FROM v_player_tournament_stats WHERE player_id = :id AND competition_id = %s
                        """.formatted(LAST_COMPETITION))
                .param("id", playerId).query(Identity.class).optional();
        return identity.map(p -> {
            PlayerReport.Totals totals = jdbcClient.sql("""
                            SELECT matches_played, matches_started, minutes_played, goals, penalty_goals, own_goals,
                                   decisive_goals, assists, yellow_cards, second_yellows, direct_reds, red_cards,
                                   fair_play_points, fouls_committed, free_kicks_won, free_kicks_conceded, duels_won,
                                   duels_lost, corners, offsides, distance_km, man_of_the_match_count, rating, rating_weighted,
                                   captain_matches, goalkeeper_matches
                            FROM v_player_tournament_stats WHERE player_id = :id AND competition_id = %s
                            """.formatted(LAST_COMPETITION))
                    .param("id", playerId).query(PlayerReport.Totals.class).single();
            List<PlayerReport.MatchLine> matches = jdbcClient.sql("""
                            SELECT m.id AS match_id, m.kickoff_at, m.stage, o.name AS opponent,
                                   tr.goals_for || '-' || tr.goals_against AS score, r.minutes_played, r.goals,
                                   (SELECT COUNT(*) FROM goal_assist a JOIN goal g ON g.id = a.goal_id
                                    WHERE g.match_id = m.id AND a.player_id = r.player_id) AS assists,
                                   r.yellow_cards, r.sent_off, r.rating
                            FROM player_match_rating r
                            JOIN match m ON m.id = r.match_id
                            JOIN team_match_rating tr ON tr.match_id = r.match_id AND tr.team_id = r.team_id
                            JOIN team o ON o.id = CASE WHEN m.team1_id = r.team_id THEN m.team2_id ELSE m.team1_id END
                            WHERE r.player_id = :id AND m.competition_id = %s
                            ORDER BY m.kickoff_at, m.id
                            """.formatted(LAST_COMPETITION))
                    .param("id", playerId).query(PlayerReport.MatchLine.class).list();
            PlayerReport.MatchLine best = matches.stream()
                    .filter(m -> m.rating() != null)
                    .max(Comparator.comparing(PlayerReport.MatchLine::rating))
                    .orElse(null);
            return new PlayerReport(p.playerId(), p.name(), p.teamId(), p.team(), Boolean.TRUE.equals(p.goalkeeper()),
                    totals, matches, best, analyseService.playerRanks(playerId), PlayerReport.FICTITIOUS_FIELDS);
        });
    }

    public Optional<TeamReport> teamReport(int teamId) {
        Optional<TeamReport.Totals> totals = jdbcClient.sql("""
                        SELECT matches_played, wins, draws, losses, points, goals_for, goals_against, yellow_cards,
                               red_cards, fair_play_points, fouls_committed, free_kicks_won, free_kicks_conceded,
                               duels_won, duels_lost, corners, offsides, distance_km, rating, stage_reached, final_position
                        FROM v_team_tournament_stats WHERE team_id = :id AND competition_id = %s
                        """.formatted(LAST_COMPETITION))
                .param("id", teamId).query(TeamReport.Totals.class).optional();
        return totals.map(t -> {
            String name = jdbcClient.sql("SELECT name FROM team WHERE id = :id").param("id", teamId)
                    .query(String.class).single();
            List<TeamReport.MatchLine> matches = jdbcClient.sql("""
                            SELECT m.id AS match_id, m.kickoff_at, m.stage, o.name AS opponent,
                                   r.goals_for || '-' || r.goals_against AS score,
                                   CASE WHEN m.pen_team1 IS NULL THEN NULL
                                        WHEN m.team1_id = r.team_id THEN m.pen_team1 || '-' || m.pen_team2
                                        ELSE m.pen_team2 || '-' || m.pen_team1 END AS penalties,
                                   r.result, r.rating
                            FROM team_match_rating r
                            JOIN match m ON m.id = r.match_id
                            JOIN team o ON o.id = CASE WHEN m.team1_id = r.team_id THEN m.team2_id ELSE m.team1_id END
                            WHERE r.team_id = :id AND m.competition_id = %s
                            ORDER BY m.kickoff_at, m.id
                            """.formatted(LAST_COMPETITION))
                    .param("id", teamId).query(TeamReport.MatchLine.class).list();
            List<TeamReport.SquadLine> squad = jdbcClient.sql("""
                            SELECT player_id, player AS name, is_goalkeeper AS goalkeeper, matches_played, minutes_played,
                                   goals, assists, yellow_cards, red_cards, rating_weighted
                            FROM v_player_tournament_stats WHERE team_id = :id AND competition_id = %s
                            ORDER BY minutes_played DESC, player
                            """.formatted(LAST_COMPETITION))
                    .param("id", teamId).query(TeamReport.SquadLine.class).list();
            TeamReport.SquadLine topScorer = squad.stream()
                    .filter(s -> s.goals() != null && s.goals() > 0)
                    .max(Comparator.comparing(TeamReport.SquadLine::goals)
                            .thenComparing(TeamReport.SquadLine::minutesPlayed, Comparator.reverseOrder()))
                    .orElse(null);
            TeamReport.SquadLine bestPlayer = squad.stream()
                    .filter(s -> s.ratingWeighted() != null)
                    .max(Comparator.comparing(TeamReport.SquadLine::ratingWeighted))
                    .orElse(null);
            return new TeamReport(teamId, name, t, matches, squad, topScorer, bestPlayer, TeamReport.FICTITIOUS_FIELDS);
        });
    }
}
