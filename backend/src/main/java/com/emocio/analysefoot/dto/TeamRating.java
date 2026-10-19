package com.emocio.analysefoot.dto;

import java.math.BigDecimal;

public record TeamRating(
        Integer teamId,
        String team,
        BigDecimal rating,
        String result,
        Integer points,
        Integer goalsFor,
        Integer goalsAgainst,
        Boolean wonShootout,
        Integer fairPlayPoints) {
}
