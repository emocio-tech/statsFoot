package com.emocio.analysefoot.dto;

import java.math.BigDecimal;

/**
 * Une ligne de classement. {@code team} est null pour une équipe ; {@code valueLabel} remplace la valeur
 * quand elle se lit mieux en mots (position finale : « champion », « finaliste »...).
 */
public record RankedEntry(
        int rank,
        String scope,
        Integer id,
        String name,
        String team,
        BigDecimal value,
        String unit,
        BigDecimal tieBreakValue,
        String valueLabel) {
}
