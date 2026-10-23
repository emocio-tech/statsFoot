package com.emocio.analysefoot.player.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes player names for searching: trims, collapses Unicode whitespace,
 * strips diacritics and lowers the case. Blank and null give an empty string.
 */
public final class NameNormalizer {

    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\p{Z}]+");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    private NameNormalizer() {
    }

    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String spaced = WHITESPACE.matcher(name).replaceAll(" ").trim();
        String decomposed = Normalizer.normalize(spaced, Normalizer.Form.NFD);
        return DIACRITICS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
