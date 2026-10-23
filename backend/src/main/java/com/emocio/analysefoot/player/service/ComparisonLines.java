package com.emocio.analysefoot.player.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.emocio.analysefoot.player.dto.Leader;
import com.emocio.analysefoot.player.dto.PlayerComparison.ComparisonLine;
import com.emocio.analysefoot.player.dto.PlayerComparison.ComparisonSummary;

/** Builds the statistic-by-statistic lines of a comparison, whatever the compared entity (player, team). */
public final class ComparisonLines {

	/** Which value leads on a statistic. */
	public enum Direction {
		HIGHER_BETTER,
		LOWER_BETTER,
		NO_LEADER
	}

	/** A compared statistic: how to read it from the statistics of an entity and who leads on it. */
	public record StatDefinition<S>(String key, String label, Direction direction, Function<S, Number> extractor) {
	}

	private ComparisonLines() {
	}

	/** Builds one line per definition, in order, for the statistics of A and B. */
	public static <S> List<ComparisonLine> build(List<StatDefinition<S>> definitions, S statsA, S statsB) {
		List<ComparisonLine> lines = new ArrayList<>();
		for (StatDefinition<S> definition : definitions) {
			lines.add(buildLine(definition, statsA, statsB));
		}
		return lines;
	}

	/** Counts the lines led by each side and the tied lines. */
	public static ComparisonSummary summarize(List<ComparisonLine> lines) {
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

	private static <S> ComparisonLine buildLine(StatDefinition<S> definition, S statsA, S statsB) {
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

}
