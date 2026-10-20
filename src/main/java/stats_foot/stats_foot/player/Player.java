package stats_foot.stats_foot.player;

import java.time.LocalDate;

/** A player with its team and statistics. {@code goalkeeperStats} is {@code null} for outfield players. */
public record Player(
		String id,
		String name,
		Team team,
		Integer shirtNumber,
		String position,
		String positionDetail,
		LocalDate birthDate,
		String club,
		Integer heightCm,
		String preferredFoot,
		Boolean captain,
		PlayerStats stats,
		GoalkeeperStats goalkeeperStats) {
}
