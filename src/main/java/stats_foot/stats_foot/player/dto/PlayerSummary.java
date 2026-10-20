package stats_foot.stats_foot.player.dto;

/** A player as listed in search results. */
public record PlayerSummary(String id, String name, String teamCode, String teamName, String position) {
}
