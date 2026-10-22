package stats_foot.stats_foot.team.model;

import stats_foot.stats_foot.player.model.Team;

/** A national team with its tournament totals. */
public record TeamWithStats(Team team, TeamStats stats) {
}
