package com.emocio.analysefoot.dto;

import java.math.BigDecimal;

public record PlayerRanking(
        Integer playerId,
        String name,
        String team,
        Integer matches,
        BigDecimal averageRating,
        Integer goals,
        Integer minutesPlayed) {
}
