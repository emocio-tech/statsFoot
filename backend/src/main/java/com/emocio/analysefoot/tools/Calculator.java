package com.emocio.analysefoot.tools;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Calcul arithmétique exact et sûr : nombres décimaux, {@code + - * / %}, parenthèses, moins unaire. Aucune variable,
 * aucune fonction, aucune exécution de code : l'entrée est analysée par descente récursive.
 */
final class Calculator {

    private static final int MAX_LENGTH = 200;
    private static final MathContext PRECISION = new MathContext(20);

    private final String text;
    private int pos;

    private Calculator(String text) {
        this.text = text;
    }

    /** @throws IllegalArgumentException expression invalide, trop longue ou division par zéro */
    static BigDecimal evaluate(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Expression vide.");
        }
        if (expression.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Expression trop longue (" + MAX_LENGTH + " caractères au plus).");
        }
        Calculator parser = new Calculator(expression.replace(',', '.'));
        BigDecimal value = parser.sum();
        parser.skipSpaces();
        if (parser.pos < parser.text.length()) {
            throw parser.error("caractère inattendu");
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
            throw new IllegalArgumentException("Division par zéro.");
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
                throw error("parenthèse fermante attendue");
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
            throw error("nombre attendu");
        }
        try {
            return new BigDecimal(text.substring(start, pos));
        } catch (NumberFormatException e) {
            throw error("nombre invalide");
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
        return new IllegalArgumentException("Expression invalide (" + reason + ", position " + (pos + 1) + ").");
    }
}
