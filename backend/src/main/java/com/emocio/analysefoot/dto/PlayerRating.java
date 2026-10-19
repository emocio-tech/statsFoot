package com.emocio.analysefoot.dto;

import java.math.BigDecimal;

public record PlayerRating(
        Integer playerId,
        Integer teamId,
        String name,
        BigDecimal rating,
        boolean starter,
        boolean captain,
        Integer minutesPlayed,
        Integer goals,
        Integer ownGoals,
        Integer decisiveGoals,
        Integer yellowCards,
        boolean sentOff,
        Integer shootoutScored,
        Integer shootoutMissed) {
}
