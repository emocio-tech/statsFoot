package stats_foot.stats_foot.player.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import stats_foot.stats_foot.player.model.PlayerStats;

/**
 * Row of the view {@code v_player_tournament_stats}: the totals of a player over the tournament.
 * The scope is a single competition, so there is one row per player. Counts and sums are read as
 * {@code Long} (BIGINT in H2); every column is nullable so that {@code null} stays distinct from {@code 0}.
 */
@Entity
@Immutable
@Table(name = "v_player_tournament_stats")
public class PlayerTournamentStatsEntity {

	@Id
	@Column(name = "player_id")
	private int playerId;

	@Column(name = "matches_played")
	private Long matchesPlayed;

	@Column(name = "matches_started")
	private Long matchesStarted;

	@Column(name = "minutes_played")
	private Long minutesPlayed;

	@Column(name = "goals")
	private Long goals;

	@Column(name = "penalty_goals")
	private Long penaltyGoals;

	@Column(name = "own_goals")
	private Long ownGoals;

	@Column(name = "yellow_cards")
	private Long yellowCards;

	@Column(name = "red_cards")
	private Long redCards;

	@Column(name = "fouls_committed")
	private Long foulsCommitted;

	@Column(name = "free_kicks_won")
	private Long freeKicksWon;

	@Column(name = "free_kicks_conceded")
	private Long freeKicksConceded;

	@Column(name = "duels_won")
	private Long duelsWon;

	@Column(name = "duels_lost")
	private Long duelsLost;

	@Column(name = "corners")
	private Long corners;

	@Column(name = "offsides")
	private Long offsides;

	@Column(name = "distance_km")
	private BigDecimal distanceKm;

	@Column(name = "man_of_the_match_count")
	private Long manOfTheMatchCount;

	@Column(name = "rating")
	private BigDecimal rating;

	@Column(name = "rating_weighted")
	private BigDecimal ratingWeighted;

	@Column(name = "goalkeeper_matches")
	private Long goalkeeperMatches;

	@Column(name = "captain_matches")
	private Long captainMatches;

	protected PlayerTournamentStatsEntity() {
	}

	public PlayerStats toStats() {
		return new PlayerStats(toInteger(matchesPlayed), toInteger(matchesStarted), toInteger(minutesPlayed),
				toInteger(goals), toInteger(penaltyGoals), toInteger(ownGoals), toInteger(yellowCards),
				toInteger(redCards), toInteger(foulsCommitted), toInteger(freeKicksWon),
				toInteger(freeKicksConceded), toInteger(duelsWon), toInteger(duelsLost), toInteger(corners),
				toInteger(offsides), toDouble(distanceKm), toInteger(manOfTheMatchCount), toDouble(rating),
				toDouble(ratingWeighted));
	}

	public int goalkeeperMatchesOrZero() {
		return goalkeeperMatches == null ? 0 : Math.toIntExact(goalkeeperMatches);
	}

	public int captainMatchesOrZero() {
		return captainMatches == null ? 0 : Math.toIntExact(captainMatches);
	}

	private static Integer toInteger(Long value) {
		return value == null ? null : Math.toIntExact(value);
	}

	private static Double toDouble(BigDecimal value) {
		return value == null ? null : value.doubleValue();
	}

}
