package com.emocio.analysefoot.team.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.emocio.analysefoot.player.entity.TeamEntity;
import com.emocio.analysefoot.team.model.TeamStats;
import com.emocio.analysefoot.team.model.TeamWithStats;

/**
 * Row of the view {@code v_team_tournament_stats}: the totals of a team over the tournament.
 * The scope is a single competition, so there is one row per team. Counts and sums are read as
 * {@code Long} (BIGINT in H2); every column is nullable so that {@code null} stays distinct from {@code 0}.
 */
@Entity
@Immutable
@Table(name = "v_team_tournament_stats")
public class TeamTournamentStatsEntity {

	@Id
	@Column(name = "team_id")
	private int teamId;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "team_id", insertable = false, updatable = false)
	private TeamEntity team;

	@Column(name = "matches_played")
	private Long matchesPlayed;

	@Column(name = "wins")
	private Long wins;

	@Column(name = "draws")
	private Long draws;

	@Column(name = "losses")
	private Long losses;

	@Column(name = "points")
	private Long points;

	@Column(name = "goals_for")
	private Long goalsFor;

	@Column(name = "goals_against")
	private Long goalsAgainst;

	@JdbcTypeCode(SqlTypes.NUMERIC)
	@Column(name = "yellow_cards")
	private Long yellowCards;

	@JdbcTypeCode(SqlTypes.NUMERIC)
	@Column(name = "red_cards")
	private Long redCards;

	@Column(name = "fair_play_points")
	private Long fairPlayPoints;

	@JdbcTypeCode(SqlTypes.NUMERIC)
	@Column(name = "fouls_committed")
	private Long foulsCommitted;

	@JdbcTypeCode(SqlTypes.NUMERIC)
	@Column(name = "free_kicks_won")
	private Long freeKicksWon;

	@JdbcTypeCode(SqlTypes.NUMERIC)
	@Column(name = "free_kicks_conceded")
	private Long freeKicksConceded;

	@JdbcTypeCode(SqlTypes.NUMERIC)
	@Column(name = "duels_won")
	private Long duelsWon;

	@JdbcTypeCode(SqlTypes.NUMERIC)
	@Column(name = "duels_lost")
	private Long duelsLost;

	@JdbcTypeCode(SqlTypes.NUMERIC)
	@Column(name = "corners")
	private Long corners;

	@JdbcTypeCode(SqlTypes.NUMERIC)
	@Column(name = "offsides")
	private Long offsides;

	@Column(name = "distance_km")
	private BigDecimal distanceKm;

	@Column(name = "rating")
	private BigDecimal rating;

	@Column(name = "final_position")
	private Integer finalPosition;

	protected TeamTournamentStatsEntity() {
	}

	public TeamWithStats toTeamWithStats() {
		return new TeamWithStats(team.toTeam(), new TeamStats(toInteger(matchesPlayed), toInteger(wins),
				toInteger(draws), toInteger(losses), toInteger(points), toInteger(goalsFor), toInteger(goalsAgainst),
				toInteger(yellowCards), toInteger(redCards), toInteger(fairPlayPoints), toInteger(foulsCommitted),
				toInteger(freeKicksWon), toInteger(freeKicksConceded), toInteger(duelsWon), toInteger(duelsLost),
				toInteger(corners), toInteger(offsides), toDouble(distanceKm), toDouble(rating),
				finalPosition));
	}

	private static Integer toInteger(Long value) {
		return value == null ? null : Math.toIntExact(value);
	}

	private static Double toDouble(BigDecimal value) {
		return value == null ? null : value.doubleValue();
	}

}
