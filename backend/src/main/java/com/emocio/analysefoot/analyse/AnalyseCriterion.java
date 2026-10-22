package com.emocio.analysefoot.analyse;

import com.emocio.analysefoot.dto.AnalyseQuestion;

import java.util.List;
import java.util.Optional;

/**
 * Catalogue fermé des questions suggérées par l'onglet Analyse (clic sur une suggestion), dans l'ordre des
 * suggestions. Il ne sert plus à interpréter une saisie : l'assistant LLM répond à toute question libre.
 * Chaque entrée porte ses fragments SQL (constantes, jamais une saisie) sur les vues du tournoi :
 * filtre, valeur affichée, tri principal puis départage. Ajouter une question = ajouter une entrée.
 */
public enum AnalyseCriterion {

    TOP_SCORER("Meilleur buteur", Scope.PLAYER,
            "goals > 0", "goals", "buts", "goals DESC, minutes_played ASC", "minutes_played", null, false,
            "Buts hors but contre son camp ; égalité départagée par le moins de minutes jouées."),

    TOP_ASSIST("Meilleur passeur", Scope.PLAYER,
            "assists > 0", "assists", "passes décisives", "assists DESC, minutes_played ASC", "minutes_played", null, true,
            "Passes décisives (données fictives) ; égalité départagée par le moins de minutes jouées."),

    MOST_YELLOW_CARDS("Joueur le plus agressif", Scope.PLAYER,
            "yellow_cards > 0", "yellow_cards", "cartons jaunes",
            "yellow_cards DESC, yellow_cards * 90.0 / NULLIF(minutes_played, 0) DESC NULLS LAST",
            "ROUND(yellow_cards * 90.0 / NULLIF(minutes_played, 0), 2)", null, false,
            "Cartons jaunes ; égalité départagée par le plus de jaunes par 90 minutes jouées."),

    LEAST_FAIR_PLAY_PLAYER("Joueur le moins fair-play", Scope.PLAYER,
            "fair_play_points < 0", "fair_play_points", "points de fair-play", "fair_play_points ASC, minutes_played ASC",
            "minutes_played", null, false,
            "Points de fair-play FIFA (jaune -1, deuxième jaune -3, rouge direct -4) ; égalité départagée par le moins "
                    + "de minutes jouées."),

    BEST_TEAM("Meilleure équipe", Scope.TEAM,
            "TRUE", "final_position", "position finale", "final_position ASC, rating DESC", "rating",
            "CASE final_position WHEN 1 THEN 'champion' WHEN 2 THEN 'finaliste' WHEN 3 THEN '3e' WHEN 4 THEN '4e' "
                    + "WHEN 5 THEN 'quart de finale' WHEN 6 THEN 'huitième de finale' WHEN 7 THEN 'seizième de finale' "
                    + "ELSE 'phase de groupes' END", false,
            "Le champion, puis les équipes par position finale ; à position égale, la meilleure note moyenne."),

    TOP_SCORING_TEAM("Équipe qui a marqué le plus de buts", Scope.TEAM,
            "TRUE", "goals_for", "buts", "goals_for DESC, goals_for - goals_against DESC", "goals_for - goals_against",
            null, false,
            "Buts marqués ; égalité départagée par la meilleure différence de buts."),

    LEAST_FAIR_PLAY_TEAM("Équipe la moins fair-play", Scope.TEAM,
            "TRUE", "fair_play_points", "points de fair-play", "fair_play_points ASC", null, null, false,
            "Points de fair-play FIFA de l'équipe (jaune -1, deuxième jaune -3, rouge direct -4) ; sans départage."),

    MOST_FAIR_PLAY_PLAYER("Joueur le plus fair-play", Scope.PLAYER,
            "NOT is_goalkeeper AND yellow_cards = 0 AND red_cards = 0", "minutes_played", "minutes sans carton",
            "minutes_played DESC", null, null, false,
            "Joueur de champ (gardiens exclus) sans aucun carton ayant joué le plus de minutes ; sans départage.");

    /** Portée d'une question : la vue du tournoi interrogée et ses colonnes d'identité. */
    public enum Scope {
        PLAYER("v_player_tournament_stats", "player_id", "player", "team"),
        TEAM("v_team_tournament_stats", "team_id", "team", "NULL");

        final String view;
        final String idColumn;
        final String nameColumn;
        final String teamColumn;

        Scope(String view, String idColumn, String nameColumn, String teamColumn) {
            this.view = view;
            this.idColumn = idColumn;
            this.nameColumn = nameColumn;
            this.teamColumn = teamColumn;
        }
    }

    private final String label;
    private final Scope scope;
    final String filter;
    final String value;
    private final String unit;
    final String order;
    final String tieBreak;
    final String valueLabel;
    private final boolean fictitious;
    private final String explanation;

    AnalyseCriterion(String label, Scope scope, String filter, String value, String unit, String order, String tieBreak,
                     String valueLabel, boolean fictitious, String explanation) {
        this.label = label;
        this.scope = scope;
        this.filter = filter;
        this.value = value;
        this.unit = unit;
        this.order = order;
        this.tieBreak = tieBreak;
        this.valueLabel = valueLabel;
        this.fictitious = fictitious;
        this.explanation = explanation;
    }

    public static Optional<AnalyseCriterion> fromId(String id) {
        for (AnalyseCriterion criterion : values()) {
            if (criterion.name().equals(id)) {
                return Optional.of(criterion);
            }
        }
        return Optional.empty();
    }

    public String label() {
        return label;
    }

    public Scope scope() {
        return scope;
    }

    public String unit() {
        return unit;
    }

    /** Vrai si le classement ou le départage utilise une donnée fictive. */
    public boolean fictitious() {
        return fictitious;
    }

    public String explanation() {
        return explanation;
    }

    public AnalyseQuestion toQuestion() {
        return new AnalyseQuestion(name(), label, scope.name(), fictitious);
    }
}
