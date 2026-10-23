package com.emocio.analysefoot.player.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.emocio.analysefoot.player.dto.PlayerComparison;
import com.emocio.analysefoot.player.dto.PlayerComparison.ComparedPlayer;
import com.emocio.analysefoot.player.dto.PlayerComparison.ComparisonLine;
import com.emocio.analysefoot.player.dto.PlayerComparison.ComparisonSummary;
import com.emocio.analysefoot.player.dto.PlayerSearchResult;
import com.emocio.analysefoot.player.dto.PlayerSummary;
import com.emocio.analysefoot.player.exception.AmbiguousPlayerNameException;
import com.emocio.analysefoot.player.exception.BlankComparisonNameException;
import com.emocio.analysefoot.player.exception.ComparisonPlayerNotFoundException;
import com.emocio.analysefoot.player.exception.InvalidSearchException;
import com.emocio.analysefoot.player.exception.SamePlayerComparisonException;
import com.emocio.analysefoot.player.model.Player;
import com.emocio.analysefoot.player.model.PlayerRole;
import com.emocio.analysefoot.player.model.PlayerStats;
import com.emocio.analysefoot.player.repository.PlayerRepository;
import com.emocio.analysefoot.player.service.ComparisonLines.Direction;
import com.emocio.analysefoot.player.service.ComparisonLines.StatDefinition;
import com.emocio.analysefoot.player.util.NameNormalizer;

/** Compares two players statistic by statistic. Free of any HTTP dependency. */
@Service
public class PlayerComparisonService {

	static final String ROLE_WARNING_TEMPLATE = "These players do not play the same role (%s vs %s): the comparison is not very relevant.";

	private static final List<StatDefinition<PlayerStats>> STATISTICS = List.of(
			new StatDefinition<>("matchesPlayed", "Matches played", Direction.NO_LEADER, PlayerStats::matchesPlayed),
			new StatDefinition<>("matchesStarted", "Matches started", Direction.NO_LEADER, PlayerStats::matchesStarted),
			new StatDefinition<>("minutesPlayed", "Minutes played", Direction.NO_LEADER, PlayerStats::minutesPlayed),
			new StatDefinition<>("goals", "Goals", Direction.HIGHER_BETTER, PlayerStats::goals),
			new StatDefinition<>("penaltyGoals", "Penalty goals", Direction.HIGHER_BETTER, PlayerStats::penaltyGoals),
			new StatDefinition<>("ownGoals", "Own goals", Direction.LOWER_BETTER, PlayerStats::ownGoals),
			new StatDefinition<>("decisiveGoals", "Decisive goals", Direction.HIGHER_BETTER, PlayerStats::decisiveGoals),
			new StatDefinition<>("assists", "Assists", Direction.HIGHER_BETTER, PlayerStats::assists),
			new StatDefinition<>("yellowCards", "Yellow cards", Direction.LOWER_BETTER, PlayerStats::yellowCards),
			new StatDefinition<>("secondYellows", "Second yellow cards", Direction.LOWER_BETTER, PlayerStats::secondYellows),
			new StatDefinition<>("directReds", "Direct red cards", Direction.LOWER_BETTER, PlayerStats::directReds),
			new StatDefinition<>("redCards", "Red cards", Direction.LOWER_BETTER, PlayerStats::redCards),
			new StatDefinition<>("fairPlayPoints", "Fair-play points", Direction.HIGHER_BETTER, PlayerStats::fairPlayPoints),
			new StatDefinition<>("foulsCommitted", "Fouls committed", Direction.LOWER_BETTER, PlayerStats::foulsCommitted),
			new StatDefinition<>("freeKicksWon", "Free kicks won", Direction.HIGHER_BETTER, PlayerStats::freeKicksWon),
			new StatDefinition<>("freeKicksConceded", "Free kicks conceded", Direction.LOWER_BETTER, PlayerStats::freeKicksConceded),
			new StatDefinition<>("duelsWon", "Duels won", Direction.HIGHER_BETTER, PlayerStats::duelsWon),
			new StatDefinition<>("duelsLost", "Duels lost", Direction.LOWER_BETTER, PlayerStats::duelsLost),
			new StatDefinition<>("corners", "Corners", Direction.HIGHER_BETTER, PlayerStats::corners),
			new StatDefinition<>("offsides", "Offsides", Direction.LOWER_BETTER, PlayerStats::offsides),
			new StatDefinition<>("distanceKm", "Distance (km)", Direction.NO_LEADER, PlayerStats::distanceKm),
			new StatDefinition<>("manOfTheMatchCount", "Man of the match", Direction.HIGHER_BETTER, PlayerStats::manOfTheMatchCount),
			new StatDefinition<>("rating", "Rating", Direction.HIGHER_BETTER, PlayerStats::rating),
			new StatDefinition<>("ratingWeighted", "Rating (weighted by minutes)", Direction.HIGHER_BETTER, PlayerStats::ratingWeighted));

	private static final int MAX_CANDIDATES = 10;

	private final PlayerRepository playerRepository;
	private final PlayerService playerService;

	public PlayerComparisonService(PlayerRepository playerRepository, PlayerService playerService) {
		this.playerRepository = playerRepository;
		this.playerService = playerService;
	}

	/**
	 * Compares two players designated by name. Each name is resolved with the player search (A first, then B).
	 *
	 * @param nameA the name of the first player
	 * @param nameB the name of the second player
	 * @return the same report as {@link #compare(int, int)} for the resolved players
	 * @throws BlankComparisonNameException      if a name is blank
	 * @throws ComparisonPlayerNotFoundException if a name matches no player
	 * @throws AmbiguousPlayerNameException      if a name matches several players
	 * @throws SamePlayerComparisonException     if both names resolve to the same player
	 */
	public PlayerComparison compareByName(String nameA, String nameB) {
		int idA = resolve(nameA, "A");
		int idB = resolve(nameB, "B");
		return compare(idA, idB);
	}

	private int resolve(String name, String label) {
		PlayerSearchResult result;
		try {
			result = playerService.search(name);
		} catch (InvalidSearchException e) {
			throw new BlankComparisonNameException("Player " + label + " name must not be blank.");
		}
		if (result.total() == 0 || result.players().isEmpty()) {
			throw new ComparisonPlayerNotFoundException("Player \"" + name + "\" not found.");
		}
		if (result.total() > 1) {
			String normalizedQuery = NameNormalizer.normalize(name);
			List<PlayerSummary> exact = result.players().stream()
					.filter(p -> NameNormalizer.normalize(p.name()).equals(normalizedQuery))
					.toList();
			if (exact.size() == 1) {
				return exact.get(0).id();
			}
			throw new AmbiguousPlayerNameException(ambiguousMessage(name, result));
		}
		return result.players().get(0).id();
	}

	private static String ambiguousMessage(String name, PlayerSearchResult result) {
		String candidates = result.players().stream()
				.limit(MAX_CANDIDATES)
				.map(p -> p.name() + " (" + p.teamCode() + ")")
				.collect(Collectors.joining(", "));
		int remaining = result.total() - MAX_CANDIDATES;
		String more = remaining > 0 ? " and " + remaining + " more" : "";
		return "Several players match the name \"" + name + "\": " + candidates + more
				+ ". Please be more precise.";
	}

	/**
	 * Compares two players.
	 *
	 * @param idA the id of the first player
	 * @param idB the id of the second player
	 * @return the comparison report
	 * @throws SamePlayerComparisonException     if both ids are equal
	 * @throws ComparisonPlayerNotFoundException if a player does not exist (idA is checked first; the message contains the missing id)
	 */
	public PlayerComparison compare(int idA, int idB) {
		if (idA == idB) {
			throw new SamePlayerComparisonException();
		}
		Player playerA = playerRepository.findById(idA)
				.orElseThrow(() -> new ComparisonPlayerNotFoundException("Player with id " + idA + " not found."));
		Player playerB = playerRepository.findById(idB)
				.orElseThrow(() -> new ComparisonPlayerNotFoundException("Player with id " + idB + " not found."));

		ComparedPlayer comparedA = toComparedPlayer(playerA);
		ComparedPlayer comparedB = toComparedPlayer(playerB);

		List<ComparisonLine> lines = ComparisonLines.build(STATISTICS, playerA.stats(), playerB.stats());
		ComparisonSummary summary = ComparisonLines.summarize(lines);
		String roleWarning = roleWarning(comparedA.role(), comparedB.role());
		String verdict = ComparisonVerdict.build(comparedA, comparedB, lines, summary, roleWarning);
		return new PlayerComparison(comparedA, comparedB, lines, summary, roleWarning, verdict);
	}

	private static ComparedPlayer toComparedPlayer(Player player) {
		PlayerRole role = player.goalkeeperMatches() > 0 ? PlayerRole.GOALKEEPER : PlayerRole.OUTFIELD_PLAYER;
		return new ComparedPlayer(player.id(), player.name(), player.team().name(), player.team().code(), role);
	}

	private static String roleWarning(PlayerRole roleA, PlayerRole roleB) {
		if (roleA == roleB) {
			return null;
		}
		return ROLE_WARNING_TEMPLATE.formatted(roleName(roleA), roleName(roleB));
	}

	private static String roleName(PlayerRole role) {
		return role == PlayerRole.GOALKEEPER ? "goalkeeper" : "outfield player";
	}

}
