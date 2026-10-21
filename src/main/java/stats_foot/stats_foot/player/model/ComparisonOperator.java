package stats_foot.stats_foot.player.model;

/** Comparison applied between a player statistic and a reference value. */
public enum ComparisonOperator {

	EQUALS,
	GREATER_THAN,
	GREATER_OR_EQUAL,
	LESS_THAN,
	LESS_OR_EQUAL;

	public boolean test(double actual, double reference) {
		return switch (this) {
			case EQUALS -> Double.compare(actual, reference) == 0;
			case GREATER_THAN -> actual > reference;
			case GREATER_OR_EQUAL -> actual >= reference;
			case LESS_THAN -> actual < reference;
			case LESS_OR_EQUAL -> actual <= reference;
		};
	}

}
