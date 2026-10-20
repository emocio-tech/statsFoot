package stats_foot.stats_foot.player;

import java.util.List;

/** Search outcome: at most 50 players, the full number of matches and whether the list was cut. */
public record PlayerSearchResult(List<PlayerSummary> players, int total, boolean truncated) {
}
