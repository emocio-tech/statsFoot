package com.emocio.analysefoot.player.model;

import java.util.function.Function;

/** A numeric player statistic usable to filter and sort players. */
public enum PlayerStat {

	MATCHES_PLAYED(p -> number(p.stats().matchesPlayed())),
	MATCHES_STARTED(p -> number(p.stats().matchesStarted())),
	MINUTES_PLAYED(p -> number(p.stats().minutesPlayed())),
	GOALS(p -> number(p.stats().goals())),
	PENALTY_GOALS(p -> number(p.stats().penaltyGoals())),
	OWN_GOALS(p -> number(p.stats().ownGoals())),
	YELLOW_CARDS(p -> number(p.stats().yellowCards())),
	RED_CARDS(p -> number(p.stats().redCards())),
	/** Yellow plus red cards: the less fair-play players have the highest value. */
	TOTAL_CARDS(p -> p.stats().yellowCards() == null || p.stats().redCards() == null ? null
			: (double) (p.stats().yellowCards() + p.stats().redCards())),
	FOULS_COMMITTED(p -> number(p.stats().foulsCommitted())),
	FREE_KICKS_WON(p -> number(p.stats().freeKicksWon())),
	FREE_KICKS_CONCEDED(p -> number(p.stats().freeKicksConceded())),
	DUELS_WON(p -> number(p.stats().duelsWon())),
	DUELS_LOST(p -> number(p.stats().duelsLost())),
	CORNERS(p -> number(p.stats().corners())),
	OFFSIDES(p -> number(p.stats().offsides())),
	DISTANCE_KM(p -> p.stats().distanceKm()),
	MAN_OF_THE_MATCH_COUNT(p -> number(p.stats().manOfTheMatchCount())),
	RATING(p -> p.stats().rating()),
	RATING_WEIGHTED(p -> p.stats().ratingWeighted()),
	ASSISTS(p -> number(p.stats().assists())),
	DECISIVE_GOALS(p -> number(p.stats().decisiveGoals())),
	SECOND_YELLOWS(p -> number(p.stats().secondYellows())),
	DIRECT_REDS(p -> number(p.stats().directReds())),
	/** FIFA fair-play points (yellow -1, second yellow -3 in all, red -4): the closer to 0, the fairer. */
	FAIR_PLAY_POINTS(p -> number(p.stats().fairPlayPoints())),
	/** Number of matches played as captain. */
	CAPTAIN_MATCHES(p -> (double) p.captainMatches()),
	/** Number of matches played as goalkeeper. */
	GOALKEEPER_MATCHES(p -> (double) p.goalkeeperMatches());

	private final Function<Player, Double> extractor;

	PlayerStat(Function<Player, Double> extractor) {
		this.extractor = extractor;
	}

	/** Returns the value of this statistic for the player, or {@code null} when not available. */
	public Double valueOf(Player player) {
		return extractor.apply(player);
	}

	private static Double number(Integer value) {
		return value == null ? null : value.doubleValue();
	}

}
