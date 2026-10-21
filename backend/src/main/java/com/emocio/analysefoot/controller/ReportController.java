package com.emocio.analysefoot.controller;

import com.emocio.analysefoot.dto.PlayerReport;
import com.emocio.analysefoot.dto.TeamReport;
import com.emocio.analysefoot.service.ReportQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rapports complets d'un joueur et d'une équipe (onglets Analyse, Joueurs et Équipes). */
@RestController
@RequestMapping("/api")
public class ReportController {

    private final ReportQueryService reportQueryService;

    public ReportController(ReportQueryService reportQueryService) {
        this.reportQueryService = reportQueryService;
    }

    @GetMapping("/players/{id}/report")
    public ResponseEntity<PlayerReport> playerReport(@PathVariable int id) {
        return ResponseEntity.of(reportQueryService.playerReport(id));
    }

    @GetMapping("/teams/{id}/report")
    public ResponseEntity<TeamReport> teamReport(@PathVariable int id) {
        return ResponseEntity.of(reportQueryService.teamReport(id));
    }
}
