package com.emocio.analysefoot.dto;

import java.util.List;

/** Détail des notes d'un match : équipes puis joueurs (titulaires d'abord, meilleure note en tête). */
public record MatchRatings(MatchSummary match, List<TeamRating> teams, List<PlayerRating> players) {
}
