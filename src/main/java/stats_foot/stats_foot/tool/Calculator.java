package stats_foot.stats_foot.tool;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Exact and safe arithmetic: decimal numbers, {@code + - * / %}, parentheses, unary minus. No variable, no function,
 * no code execution: the input is parsed by recursive descent.
 */
final class Calculator {

	private static final int MAX_LENGTH = 200;
	private static final MathContext PRECISION = new MathContext(20);

	private final String text;
	private int pos;

	private Calculator(String text) {
		this.text = text;
	}

	/** @throws IllegalArgumentException invalid or too long expression, or division by zero */
	static BigDecimal evaluate(String expression) {
		if (expression == null || expression.isBlank()) {
			throw new IllegalArgumentException("Empty expression.");
		}
		if (expression.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("Expression too long (" + MAX_LENGTH + " characters at most).");
		}
		Calculator parser = new Calculator(expression.replace(',', '.'));
		BigDecimal value = parser.sum();
		parser.skipSpaces();
		if (parser.pos < parser.text.length()) {
			throw parser.error("unexpected character");
		}
		return value.stripTrailingZeros();
	}

	private BigDecimal sum() {
		BigDecimal value = product();
		while (true) {
			skipSpaces();
			if (accept('+')) {
				value = value.add(product(), PRECISION);
			} else if (accept('-')) {
				value = value.subtract(product(), PRECISION);
			} else {
				return value;
			}
		}
	}

	private BigDecimal product() {
		BigDecimal value = unary();
		while (true) {
			skipSpaces();
			if (accept('*')) {
				value = value.multiply(unary(), PRECISION);
			} else if (accept('/')) {
				value = value.divide(nonZero(unary()), PRECISION);
			} else if (accept('%')) {
				value = value.remainder(nonZero(unary()), PRECISION);
			} else {
				return value;
			}
		}
	}

	private static BigDecimal nonZero(BigDecimal divisor) {
		if (divisor.signum() == 0) {
			throw new IllegalArgumentException("Division by zero.");
		}
		return divisor;
	}

	private BigDecimal unary() {
		skipSpaces();
		if (accept('-')) {
			return unary().negate();
		}
		if (accept('+')) {
			return unary();
		}
		if (accept('(')) {
			BigDecimal value = sum();
			skipSpaces();
			if (!accept(')')) {
				throw error("closing parenthesis expected");
			}
			return value;
		}
		return number();
	}

	private BigDecimal number() {
		int start = pos;
		while (pos < text.length() && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.')) {
			pos++;
		}
		if (start == pos) {
			throw error("number expected");
		}
		try {
			return new BigDecimal(text.substring(start, pos));
		} catch (NumberFormatException e) {
			throw error("invalid number");
		}
	}

	private boolean accept(char c) {
		if (pos < text.length() && text.charAt(pos) == c) {
			pos++;
			return true;
		}
		return false;
	}

	private void skipSpaces() {
		while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
			pos++;
		}
	}

	private IllegalArgumentException error(String reason) {
		return new IllegalArgumentException("Invalid expression (" + reason + ", position " + (pos + 1) + ").");
	}

}
