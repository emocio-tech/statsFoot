package com.emocio.analysefoot.tools;

import java.text.Normalizer;
import java.util.Locale;

/** Normalisation des saisies et des noms pour les comparer : minuscules, sans accents ni ponctuation. */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    /** Minuscules, sans accents, ponctuation remplacée par des espaces, espaces simples. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
