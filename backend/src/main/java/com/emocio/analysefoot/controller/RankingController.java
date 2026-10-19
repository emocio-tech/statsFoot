package com.emocio.analysefoot.controller;

import com.emocio.analysefoot.dto.PlayerRanking;
import com.emocio.analysefoot.dto.TeamRanking;
import com.emocio.analysefoot.service.RatingQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rankings")
public class RankingController {

    private final RatingQueryService ratingQueryService;

    public RankingController(RatingQueryService ratingQueryService) {
        this.ratingQueryService = ratingQueryService;
    }

    /** Joueurs classés par note moyenne, parmi ceux ayant joué au moins {@code minMatches} matchs. */
    @GetMapping("/players")
    public List<PlayerRanking> players(@RequestParam(defaultValue = "3") @Min(1) int minMatches,
                                       @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit) {
        return ratingQueryService.findPlayerRanking(minMatches, limit);
    }

    @GetMapping("/teams")
    public List<TeamRanking> teams() {
        return ratingQueryService.findTeamRanking();
    }
}
