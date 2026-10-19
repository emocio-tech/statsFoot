package com.emocio.analysefoot.controller;

import com.emocio.analysefoot.dto.MatchRatings;
import com.emocio.analysefoot.dto.MatchSummary;
import com.emocio.analysefoot.service.RatingQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final RatingQueryService ratingQueryService;

    public MatchController(RatingQueryService ratingQueryService) {
        this.ratingQueryService = ratingQueryService;
    }

    @GetMapping
    public List<MatchSummary> matches() {
        return ratingQueryService.findMatches();
    }

    @GetMapping("/{id}/ratings")
    public ResponseEntity<MatchRatings> ratings(@PathVariable int id) {
        return ResponseEntity.of(ratingQueryService.findMatchRatings(id));
    }
}
