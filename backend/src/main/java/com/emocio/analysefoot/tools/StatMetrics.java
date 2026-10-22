package com.emocio.analysefoot.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Les statistiques de joueur et d'équipe lisibles par les tools {@code stat} et {@code rank} : liste fermée, avec
 * l'expression SQL de chacune sur les vues du tournoi. Ce qui entre dans une requête vient toujours d'ici, jamais
 * d'une saisie.
 */
public final class StatMetrics {

    /** Statistiques de joueur (colonnes de v_player_tournament_stats), libellé français pour l'unité et les titres. */
    private static final Map<String, String> PLAYER = new LinkedHashMap<>();

    /** Statistiques d'équipe (colonnes de v_team_tournament_stats, plus l'écart de buts). */
    private static final Map<String, String> TEAM = new LinkedHashMap<>();

    /** Expression SQL des statistiques d'équipe qui ne sont pas une colonne. */
    private static final Map<String, String> TEAM_EXPRESSION = Map.of("goal_difference", "goals_for - goals_against");

    /** Statistiques détaillées inventées (partie 6 de donneesStats.sql) et passes décisives : données FICTIVES. */
    private static final Set<String> FICTITIOUS = Set.of("assists", "fouls_committed", "free_kicks_won",
            "free_kicks_conceded", "duels_won", "duels_lost", "corners", "offsides", "distance_km",
            "man_of_the_match_count");

    static {
        PLAYER.put("matches_played", "matchs joués");
        PLAYER.put("matches_started", "matchs comme titulaire");
        PLAYER.put("minutes_played", "minutes jouées");
        PLAYER.put("goals", "buts");
        PLAYER.put("penalty_goals", "buts sur penalty");
        PLAYER.put("own_goals", "buts contre son camp");
        PLAYER.put("decisive_goals", "buts décisifs");
        PLAYER.put("assists", "passes décisives");
        PLAYER.put("yellow_cards", "cartons jaunes");
        PLAYER.put("red_cards", "cartons rouges");
        PLAYER.put("second_yellows", "doubles jaunes");
        PLAYER.put("direct_reds", "rouges directs");
        PLAYER.put("fair_play_points", "points de fair-play");
        PLAYER.put("fouls_committed", "fautes commises");
        PLAYER.put("free_kicks_won", "coups francs obtenus");
        PLAYER.put("free_kicks_conceded", "coups francs concédés");
        PLAYER.put("duels_won", "duels gagnés");
        PLAYER.put("duels_lost", "duels perdus");
        PLAYER.put("corners", "corners");
        PLAYER.put("offsides", "hors-jeu");
        PLAYER.put("distance_km", "km parcourus");
        PLAYER.put("man_of_the_match_count", "titres d'homme du match");
        PLAYER.put("rating", "note moyenne");
        PLAYER.put("rating_weighted", "note pondérée par le temps de jeu");

        TEAM.put("matches_played", "matchs joués");
        TEAM.put("wins", "victoires");
        TEAM.put("draws", "nuls");
        TEAM.put("losses", "défaites");
        TEAM.put("points", "points");
        TEAM.put("goals_for", "buts marqués");
        TEAM.put("goals_against", "buts encaissés");
        TEAM.put("goal_difference", "différence de buts");
        TEAM.put("yellow_cards", "cartons jaunes");
        TEAM.put("red_cards", "cartons rouges");
        TEAM.put("fair_play_points", "points de fair-play");
        TEAM.put("fouls_committed", "fautes commises");
        TEAM.put("free_kicks_won", "coups francs obtenus");
        TEAM.put("free_kicks_conceded", "coups francs concédés");
        TEAM.put("duels_won", "duels gagnés");
        TEAM.put("duels_lost", "duels perdus");
        TEAM.put("corners", "corners");
        TEAM.put("offsides", "hors-jeu");
        TEAM.put("distance_km", "km parcourus");
        TEAM.put("rating", "note moyenne");
        TEAM.put("final_position", "position finale");
    }

    private StatMetrics() {
    }

    public static boolean isPlayerScope(String scope) {
        return "PLAYER".equalsIgnoreCase(scope);
    }

    /** Noms des statistiques du périmètre ({@code PLAYER} ou {@code TEAM}). */
    public static List<String> names(boolean players) {
        return List.copyOf((players ? PLAYER : TEAM).keySet());
    }

    /** Noms de toutes les statistiques, joueur puis équipe, sans doublon. */
    public static List<String> allNames() {
        Set<String> all = new java.util.LinkedHashSet<>(PLAYER.keySet());
        all.addAll(TEAM.keySet());
        return List.copyOf(all);
    }

    /** Expression SQL de la statistique, ou null si elle n'existe pas pour ce périmètre. */
    public static String expression(boolean players, String metric) {
        if (metric == null || !(players ? PLAYER : TEAM).containsKey(metric)) {
            return null;
        }
        return players ? metric : TEAM_EXPRESSION.getOrDefault(metric, metric);
    }

    /** Libellé français (unité) de la statistique ; son nom si elle est inconnue. */
    public static String label(boolean players, String metric) {
        return (players ? PLAYER : TEAM).getOrDefault(metric, metric);
    }

    public static boolean isFictitious(String metric) {
        return FICTITIOUS.contains(metric);
    }
}
