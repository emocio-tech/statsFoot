package stats_foot.stats_foot.player;

/** Goalkeeper-specific tournament statistics. A {@code null} value means "not available". */
public record GoalkeeperStats(Integer saves, Integer goalsConceded, Integer cleanSheets, Integer penaltiesSaved) {
}
