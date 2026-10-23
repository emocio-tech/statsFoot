package com.emocio.analysefoot.team.model;

import com.emocio.analysefoot.player.model.Team;

/** A national team with its tournament totals. */
public record TeamWithStats(Team team, TeamStats stats) {
}
