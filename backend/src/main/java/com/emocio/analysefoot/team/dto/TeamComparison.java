package com.emocio.analysefoot.team.dto;

import java.util.List;

import com.emocio.analysefoot.player.dto.PlayerComparison.ComparisonLine;
import com.emocio.analysefoot.player.dto.PlayerComparison.ComparisonSummary;

/** A comparison report of two teams as exposed by the API; lines and summary are those of a player comparison. */
public record TeamComparison(
		ComparedTeam teamA,
		ComparedTeam teamB,
		List<ComparisonLine> lines,
		ComparisonSummary summary,
		String verdict) {

	/** Identity of a compared team. */
	public record ComparedTeam(int id, String name, String code) {
	}

}
