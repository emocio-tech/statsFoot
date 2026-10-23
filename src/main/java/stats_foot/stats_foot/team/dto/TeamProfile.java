package stats_foot.stats_foot.team.dto;

import stats_foot.stats_foot.team.model.TeamStats;

/** A team profile as exposed by the tools. Totals keep {@code null} (kept in the JSON) when not available. */
public record TeamProfile(int id, String name, String code, TeamStats stats) {
}
