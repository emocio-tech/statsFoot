package com.emocio.analysefoot.dto;

/** Une question du catalogue de l'onglet Analyse. */
public record AnalyseQuestion(String id, String label, String scope, boolean fictitious) {
}
