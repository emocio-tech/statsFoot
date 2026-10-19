package com.emocio.analysefoot.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Match avec son score final et la note de chaque équipe. */
public record MatchSummary(
        Integer id,
        String stage,
        String groupName,
        OffsetDateTime kickoffAt,
        String stadium,
        String city,
        Integer team1Id,
        String team1,
        Integer team2Id,
        String team2,
        Integer score1,
        Integer score2,
        boolean extraTime,
        Integer penalties1,
        Integer penalties2,
        BigDecimal rating1,
        BigDecimal rating2) {
}
