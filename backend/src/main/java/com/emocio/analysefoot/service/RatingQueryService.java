package com.emocio.analysefoot.service;

import com.emocio.analysefoot.dto.MatchRatings;
import com.emocio.analysefoot.dto.MatchSummary;
import com.emocio.analysefoot.dto.PlayerRanking;
import com.emocio.analysefoot.dto.PlayerRating;
import com.emocio.analysefoot.dto.TeamRanking;
import com.emocio.analysefoot.dto.TeamRating;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Lecture des notes calculées par le skill notation-matchs.
 * Les tables de notes sont alimentées hors de l'application, d'où des requêtes SQL directes.
 */
@Service
public class RatingQueryService {

    private static final String MATCH_SUMMARY_SQL = """
            SELECT m.id, m.stage, m.group_name, m.kickoff_at, s.name AS stadium, s.city,
                   m.team1_id, t1.name AS team1, m.team2_id, t2.name AS team2,
                   COALESCE(m.et_team1, m.ft_team1) AS score1, COALESCE(m.et_team2, m.ft_team2) AS score2,
                   m.et_team1 IS NOT NULL AS extra_time,
                   m.pen_team1 AS penalties1, m.pen_team2 AS penalties2,
                   r1.rating AS rating1, r2.rating AS rating2
            FROM match m
            JOIN stadium s ON s.id = m.stadium_id
            JOIN team t1 ON t1.id = m.team1_id
            JOIN team t2 ON t2.id = m.team2_id
            LEFT JOIN team_match_rating r1 ON r1.match_id = m.id AND r1.team_id = m.team1_id
            LEFT JOIN team_match_rating r2 ON r2.match_id = m.id AND r2.team_id = m.team2_id
            """;

    private final JdbcClient jdbcClient;

    public RatingQueryService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<MatchSummary> findMatches() {
        return jdbcClient.sql(MATCH_SUMMARY_SQL + " ORDER BY m.kickoff_at, m.id")
                .query(MatchSummary.class)
                .list();
    }

    public Optional<MatchRatings> findMatchRatings(int matchId) {
        return jdbcClient.sql(MATCH_SUMMARY_SQL + " WHERE m.id = :id")
                .param("id", matchId)
                .query(MatchSummary.class)
                .optional()
                .map(match -> new MatchRatings(match, findTeamRatings(matchId), findPlayerRatings(matchId)));
    }

    private List<TeamRating> findTeamRatings(int matchId) {
        return jdbcClient.sql("""
                        SELECT r.team_id, t.name AS team, r.rating, r.result, r.points,
                               r.goals_for, r.goals_against, r.won_shootout, r.fair_play_points
                        FROM team_match_rating r
                        JOIN team t ON t.id = r.team_id
                        JOIN match m ON m.id = r.match_id
                        WHERE r.match_id = :id
                        ORDER BY r.team_id = m.team1_id DESC
                        """)
                .param("id", matchId)
                .query(TeamRating.class)
                .list();
    }

    private List<PlayerRating> findPlayerRatings(int matchId) {
        return jdbcClient.sql("""
                        SELECT r.player_id, r.team_id, p.name, r.rating, l.is_starter AS starter, l.is_captain AS captain,
                               r.minutes_played, r.goals, r.own_goals, r.decisive_goals, r.yellow_cards, r.sent_off,
                               r.shootout_scored, r.shootout_missed
                        FROM player_match_rating r
                        JOIN player p ON p.id = r.player_id
                        JOIN match_lineup l ON l.match_id = r.match_id AND l.player_id = r.player_id
                        WHERE r.match_id = :id
                        ORDER BY l.is_starter DESC, r.rating DESC, p.name
                        """)
                .param("id", matchId)
                .query(PlayerRating.class)
                .list();
    }

    public List<PlayerRanking> findPlayerRanking(int minMatches, int limit) {
        return jdbcClient.sql("""
                        SELECT p.id AS player_id, p.name, t.name AS team, COUNT(*) AS matches,
                               ROUND(AVG(r.rating), 2) AS average_rating,
                               SUM(r.goals) AS goals, SUM(r.minutes_played) AS minutes_played
                        FROM player_match_rating r
                        JOIN player p ON p.id = r.player_id
                        JOIN team t ON t.id = r.team_id
                        GROUP BY p.id, p.name, t.name
                        HAVING COUNT(*) >= :minMatches
                        ORDER BY average_rating DESC, goals DESC, p.name
                        LIMIT :limit
                        """)
                .param("minMatches", minMatches)
                .param("limit", limit)
                .query(PlayerRanking.class)
                .list();
    }

    public List<TeamRanking> findTeamRanking() {
        return jdbcClient.sql("""
                        SELECT t.id AS team_id, t.name AS team, COUNT(*) AS matches,
                               ROUND(AVG(r.rating), 2) AS average_rating, SUM(r.points) AS points,
                               SUM(r.goals_for) AS goals_for, SUM(r.goals_against) AS goals_against,
                               SUM(r.fair_play_points) AS fair_play_points
                        FROM team_match_rating r
                        JOIN team t ON t.id = r.team_id
                        GROUP BY t.id, t.name
                        ORDER BY average_rating DESC, points DESC, t.name
                        """)
                .query(TeamRanking.class)
                .list();
    }
}
