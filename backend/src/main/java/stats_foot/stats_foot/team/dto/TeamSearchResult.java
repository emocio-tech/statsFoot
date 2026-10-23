package stats_foot.stats_foot.team.dto;

import java.util.List;

/** Search outcome: the matching teams (there are at most 48 teams, so the list is never cut). */
public record TeamSearchResult(List<TeamSummary> teams, int total) {
}
