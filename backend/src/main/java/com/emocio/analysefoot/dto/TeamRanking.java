package com.emocio.analysefoot.dto;

import java.math.BigDecimal;

public record TeamRanking(
        Integer teamId,
        String team,
        Integer matches,
        BigDecimal averageRating,
        Integer points,
        Integer goalsFor,
        Integer goalsAgainst,
        Integer fairPlayPoints) {
}
