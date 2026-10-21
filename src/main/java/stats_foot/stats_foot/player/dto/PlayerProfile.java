package stats_foot.stats_foot.player.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonInclude;
import stats_foot.stats_foot.player.model.GoalkeeperStats;
import stats_foot.stats_foot.player.model.PlayerStats;

/**
 * A player profile as exposed by the API. {@code goalkeeperStats} is {@code null} (and omitted from the JSON)
 * for outfield players; statistics keep {@code null} when not available.
 */
public record PlayerProfile(
		String id,
		String name,
		String teamCode,
		String teamName,
		Integer shirtNumber,
		String position,
		String positionDetail,
		LocalDate birthDate,
		int age,
		String club,
		Integer heightCm,
		String preferredFoot,
		Boolean captain,
		PlayerStats stats,
		@JsonInclude(JsonInclude.Include.NON_NULL) GoalkeeperStats goalkeeperStats) {
}
