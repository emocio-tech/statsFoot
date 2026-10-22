package stats_foot.stats_foot.player.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import stats_foot.stats_foot.player.dto.Leader;
import stats_foot.stats_foot.player.dto.PlayerComparison;
import stats_foot.stats_foot.player.dto.PlayerComparison.ComparedPlayer;
import stats_foot.stats_foot.player.dto.PlayerComparison.ComparisonLine;
import stats_foot.stats_foot.player.dto.PlayerComparison.ComparisonSummary;
import stats_foot.stats_foot.player.dto.PlayerSearchResult;
import stats_foot.stats_foot.player.dto.PlayerSummary;
import stats_foot.stats_foot.player.exception.AmbiguousPlayerNameException;
import stats_foot.stats_foot.player.exception.BlankComparisonNameException;
import stats_foot.stats_foot.player.exception.ComparisonPlayerNotFoundException;
import stats_foot.stats_foot.player.exception.InvalidSearchException;
import stats_foot.stats_foot.player.exception.SamePlayerComparisonException;
import stats_foot.stats_foot.player.model.Player;
import stats_foot.stats_foot.player.model.PlayerRole;
import stats_foot.stats_foot.player.model.PlayerStats;
import stats_foot.stats_foot.player.repository.PlayerRepository;
import stats_foot.stats_foot.player.util.NameNormalizer;

/** Compares two players statistic by statistic. Free of any HTTP dependency. */
@Service
public class PlayerComparisonService {

	static final String ROLE_WARNING_TEMPLATE = "These players do not play the same role (%s vs %s): the comparison is not very relevant.";

	private enum Direction {
		HIGHER_BETTER,
		LOWER_BETTER,
		NO_LEADER
	}

	private record StatDefinition(String key, String label, Direction direction, Function<PlayerStats, Number> extractor) {
	}

	private static final List<StatDefinition> STATISTICS = List.of(
			new StatDefinition("matchesPlayed", "Matches played", Direction.NO_LEADER, PlayerStats::matchesPlayed),
			new StatDefinition("matchesStarted", "Matches started", Direction.NO_LEADER, PlayerStats::matchesStarted),
			new StatDefinition("minutesPlayed", "Minutes played", Direction.NO_LEADER, PlayerStats::minutesPlayed),
			new StatDefinition("goals", "Goals", Direction.HIGHER_BETTER, PlayerStats::goals),
			new StatDefinition("penaltyGoals", "Penalty goals", Direction.HIGHER_BETTER, PlayerStats::penaltyGoals),
			new StatDefinition("ownGoals", "Own goals", Direction.LOWER_BETTER, PlayerStats::ownGoals),
			new StatDefinition("yellowCards", "Yellow cards", Direction.LOWER_BETTER, PlayerStats::yellowCards),
			new StatDefinition("redCards", "Red cards", Direction.LOWER_BETTER, PlayerStats::redCards),
			new StatDefinition("foulsCommitted", "Fouls committed", Direction.LOWER_BETTER, PlayerStats::foulsCommitted),
			new StatDefinition("freeKicksWon", "Free kicks won", Direction.HIGHER_BETTER, PlayerStats::freeKicksWon),
			new StatDefinition("freeKicksConceded", "Free kicks conceded", Direction.LOWER_BETTER, PlayerStats::freeKicksConceded),
			new StatDefinition("duelsWon", "Duels won", Direction.HIGHER_BETTER, PlayerStats::duelsWon),
			new StatDefinition("duelsLost", "Duels lost", Direction.LOWER_BETTER, PlayerStats::duelsLost),
			new StatDefinition("corners", "Corners", Direction.HIGHER_BETTER, PlayerStats::corners),
			new StatDefinition("offsides", "Offsides", Direction.LOWER_BETTER, PlayerStats::offsides),
			new StatDefinition("distanceKm", "Distance (km)", Direction.NO_LEADER, PlayerStats::distanceKm),
			new StatDefinition("manOfTheMatchCount", "Man of the match", Direction.HIGHER_BETTER, PlayerStats::manOfTheMatchCount),
			new StatDefinition("rating", "Rating", Direction.HIGHER_BETTER, PlayerStats::rating),
			new StatDefinition("ratingWeighted", "Rating (weighted by minutes)", Direction.HIGHER_BETTER, PlayerStats::ratingWeighted));

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
			throw new ComparisonPlayerNotFoundException("Player " + label + " not found.");
		}
		if (result.total() > 1) {
			String normalizedQuery = NameNormalizer.normalize(name);
			List<PlayerSummary> exact = result.players().stream()
					.filter(p -> NameNormalizer.normalize(p.name()).equals(normalizedQuery))
					.toList();
			if (exact.size() == 1) {
				return exact.get(0).id();
			}
			throw new AmbiguousPlayerNameException(ambiguousMessage(label, result));
		}
		return result.players().get(0).id();
	}

	private static String ambiguousMessage(String label, PlayerSearchResult result) {
		String candidates = result.players().stream()
				.limit(MAX_CANDIDATES)
				.map(p -> p.name() + " (" + p.teamCode() + ")")
				.collect(Collectors.joining(", "));
		int remaining = result.total() - MAX_CANDIDATES;
		String more = remaining > 0 ? " and " + remaining + " more" : "";
		return "Several players match the name of player " + label + ": " + candidates + more
				+ ". Please be more precise.";
	}

	/**
	 * Compares two players.
	 *
	 * @param idA the id of the first player
	 * @param idB the id of the second player
	 * @return the comparison report
	 * @throws SamePlayerComparisonException     if both ids are equal
	 * @throws ComparisonPlayerNotFoundException if a player does not exist (A is named first)
	 */
	public PlayerComparison compare(int idA, int idB) {
		if (idA == idB) {
			throw new SamePlayerComparisonException();
		}
		Player playerA = playerRepository.findById(idA)
				.orElseThrow(() -> new ComparisonPlayerNotFoundException("Player A not found."));
		Player playerB = playerRepository.findById(idB)
				.orElseThrow(() -> new ComparisonPlayerNotFoundException("Player B not found."));

		ComparedPlayer comparedA = toComparedPlayer(playerA);
		ComparedPlayer comparedB = toComparedPlayer(playerB);

		List<ComparisonLine> lines = new ArrayList<>();
		for (StatDefinition definition : STATISTICS) {
			lines.add(buildLine(definition, playerA.stats(), playerB.stats()));
		}
		ComparisonSummary summary = summarize(lines);
		String roleWarning = roleWarning(comparedA.role(), comparedB.role());
		String verdict = ComparisonVerdict.build(comparedA, comparedB, lines, summary, roleWarning);
		return new PlayerComparison(comparedA, comparedB, lines, summary, roleWarning, verdict);
	}

	private static ComparedPlayer toComparedPlayer(Player player) {
		PlayerRole role = player.goalkeeperMatches() > 0 ? PlayerRole.GOALKEEPER : PlayerRole.OUTFIELD_PLAYER;
		return new ComparedPlayer(player.id(), player.name(), player.team().name(), player.team().code(), role);
	}

	private static ComparisonLine buildLine(StatDefinition definition, PlayerStats statsA, PlayerStats statsB) {
		Number valueA = definition.extractor().apply(statsA);
		Number valueB = definition.extractor().apply(statsB);
		if (valueA == null || valueB == null) {
			return new ComparisonLine(definition.key(), definition.label(), valueA, valueB, null, null, false);
		}
		Number difference = difference(valueA, valueB);
		Leader leader = leader(definition.direction(), difference.doubleValue());
		return new ComparisonLine(definition.key(), definition.label(), valueA, valueB, difference, leader, true);
	}

	private static Number difference(Number valueA, Number valueB) {
		if (valueA instanceof Integer a && valueB instanceof Integer b) {
			return a - b;
		}
		return BigDecimal.valueOf(valueA.doubleValue()).subtract(BigDecimal.valueOf(valueB.doubleValue()))
				.setScale(2, RoundingMode.HALF_UP).doubleValue();
	}

	private static Leader leader(Direction direction, double difference) {
		if (direction == Direction.NO_LEADER) {
			return null;
		}
		if (difference == 0) {
			return Leader.TIE;
		}
		boolean aLeads = direction == Direction.HIGHER_BETTER ? difference > 0 : difference < 0;
		return aLeads ? Leader.A : Leader.B;
	}

	private static ComparisonSummary summarize(List<ComparisonLine> lines) {
		int leadsA = 0;
		int leadsB = 0;
		int ties = 0;
		for (ComparisonLine line : lines) {
			if (line.leader() == Leader.A) {
				leadsA++;
			} else if (line.leader() == Leader.B) {
				leadsB++;
			} else if (line.leader() == Leader.TIE) {
				ties++;
			}
		}
		return new ComparisonSummary(leadsA, leadsB, ties);
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
