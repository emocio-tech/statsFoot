package com.emocio.analysefoot.controller;

import com.emocio.analysefoot.dto.PlayerTournamentStats;
import com.emocio.analysefoot.dto.TeamTournamentStats;
import com.emocio.analysefoot.service.TournamentStatsQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Statistiques cumulées sur un tournoi (onglets Joueurs et Équipes). */
@RestController
@RequestMapping("/api/stats")
public class TournamentStatsController {

    private final TournamentStatsQueryService statsQueryService;

    public TournamentStatsController(TournamentStatsQueryService statsQueryService) {
        this.statsQueryService = statsQueryService;
    }

    @GetMapping("/players")
    public List<PlayerTournamentStats> players(@RequestParam(required = false) Integer competitionId) {
        return statsQueryService.findPlayerStats(competitionId);
    }

    @GetMapping("/teams")
    public List<TeamTournamentStats> teams(@RequestParam(required = false) Integer competitionId) {
        return statsQueryService.findTeamStats(competitionId);
    }
}
