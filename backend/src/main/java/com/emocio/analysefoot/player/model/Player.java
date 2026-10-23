package com.emocio.analysefoot.player.model;

/** A player with its team, goalkeeper and captain match counts, and tournament totals. */
public record Player(
		int id,
		String name,
		Team team,
		int goalkeeperMatches,
		int captainMatches,
		PlayerStats stats) {
}
