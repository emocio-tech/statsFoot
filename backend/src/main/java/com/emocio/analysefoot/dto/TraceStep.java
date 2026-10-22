package com.emocio.analysefoot.dto;

/**
 * Une étape de la trace d'une recherche : {@code actor} APP, LLM, TOOL, MCP ou SQL ; {@code direction} OUT (appel,
 * aller) ou IN (retour) ; {@code ms} = délai depuis le début de la requête.
 */
public record TraceStep(String actor, String direction, String label, String detail, long ms) {
}
