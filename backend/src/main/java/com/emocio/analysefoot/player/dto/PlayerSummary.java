package com.emocio.analysefoot.player.dto;

/** A player as listed in search results. */
public record PlayerSummary(int id, String name, String teamCode, String teamName) {
}
