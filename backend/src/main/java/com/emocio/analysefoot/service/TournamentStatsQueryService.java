package com.emocio.analysefoot.service;

import com.emocio.analysefoot.dto.PlayerTournamentStats;
import com.emocio.analysefoot.dto.TeamTournamentStats;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Lecture des vues v_player_tournament_stats et v_team_tournament_stats (migration V3).
 * Sans compétition précisée, la plus récemment importée est utilisée.
 */
@Service
public class TournamentStatsQueryService {

    private static final String COMPETITION_FILTER =
            "competition_id = COALESCE(CAST(:competitionId AS INT), (SELECT MAX(id) FROM competition))";

    private final JdbcClient jdbcClient;

    public TournamentStatsQueryService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<PlayerTournamentStats> findPlayerStats(Integer competitionId) {
        return jdbcClient.sql("SELECT * FROM v_player_tournament_stats WHERE " + COMPETITION_FILTER
                        + " ORDER BY rating_weighted DESC NULLS LAST, minutes_played DESC, player")
                .param("competitionId", competitionId)
                .query(PlayerTournamentStats.class)
                .list();
    }

    public List<TeamTournamentStats> findTeamStats(Integer competitionId) {
        return jdbcClient.sql("SELECT * FROM v_team_tournament_stats WHERE " + COMPETITION_FILTER
                        + " ORDER BY rating DESC, points DESC, goals_for - goals_against DESC, team")
                .param("competitionId", competitionId)
                .query(TeamTournamentStats.class)
                .list();
    }
}
