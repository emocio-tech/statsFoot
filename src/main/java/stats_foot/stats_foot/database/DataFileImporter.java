package stats_foot.stats_foot.database;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads {@code donneesStats.json} into the tables created by {@code db/schema.sql}.
 *
 * <p>The importer opens no transaction and never commits by itself: the caller decides
 * (all inserts must run on the same connection/transaction for an all-or-nothing import).
 * Required fields are validated (missing or null: {@link IllegalStateException}). Identifiers of the file are kept for competition, team, player and match; stadiums and
 * referees get generated identifiers. NULL values of the file stay NULL.
 */
public class DataFileImporter {

    static final String ALGORITHM_VERSION = "v1";

    private final JdbcTemplate jdbc;
    private final Path file;

    public DataFileImporter(JdbcTemplate jdbc, Path file) {
        this.jdbc = jdbc;
        this.file = file;
    }

    /** Reads the file and inserts everything. Identity columns are restarted by {@link #restartIdentities()}. */
    public void importFile() {
        JsonNode root;
        try {
            root = JsonMapper.builder().build().readTree(file.toFile());
        } catch (RuntimeException e) {
            throw new IllegalStateException("Cannot read the data file.", e);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalStateException("Invalid data file.");
        }

        insertCompetition(root.path("competition"));
        insertTeams(root.path("teams"));
        insertPlayers(root.path("players"));

        JsonNode matches = root.path("matches");
        Map<String, Integer> stadiums = insertStadiums(matches);
        Map<String, Integer> referees = insertReferees(matches);
        insertMatches(matches, stadiums, referees);
        insertLineups(matches);
        insertSubstitutions(matches);
        insertGoals(matches);
        insertBookings(matches);
        insertPenaltyKicks(matches);
        insertPlayerRatings(matches);
        insertTeamRatings(matches);
        insertPlayerStats(matches);
        insertTeamStats(matches);
    }

    /**
     * Sets every identity column to max(id) + 1 so that inserts without an id do not collide with the
     * imported rows. Idempotent. Must run outside the import transaction: H2 commits implicitly on each DDL.
     */
    public void restartIdentities() {
        for (String table : List.of("competition", "team", "stadium", "referee", "player", "match", "substitution",
                "goal", "booking")) {
            Integer max = jdbc.queryForObject("SELECT MAX(id) FROM " + table, Integer.class);
            int next = (max == null ? 0 : max) + 1;
            jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN id RESTART WITH " + next);
        }
    }

    // Reference data -------------------------------------------------------------

    private void insertCompetition(JsonNode competition) {
        jdbc.update("INSERT INTO competition (id, name) VALUES (?, ?)", requiredInt(competition, "id", "competition"),
                requiredText(competition, "name", "competition"));
    }

    private void insertTeams(JsonNode teams) {
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode t : teams) {
            rows.add(new Object[] { requiredInt(t, "id", "team"), requiredText(t, "name", "team"), requiredText(t, "fifaCode", "team") });
        }
        jdbc.batchUpdate("INSERT INTO team (id, name, fifa_code) VALUES (?, ?, ?)", rows);
    }

    private void insertPlayers(JsonNode players) {
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode p : players) {
            rows.add(new Object[] { requiredInt(p, "id", "player"), requiredText(p, "name", "player"), requiredInt(p, "teamId", "player") });
        }
        jdbc.batchUpdate("INSERT INTO player (id, name, team_id) VALUES (?, ?, ?)", rows);
    }

    private Map<String, Integer> insertStadiums(JsonNode matches) {
        Map<String, Integer> ids = new HashMap<>();
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode m : matches) {
            String name = requiredText(m, "stadium", "match");
            String city = requiredText(m, "city", "match");
            String key = stadiumKey(name, city);
            if (!ids.containsKey(key)) {
                ids.put(key, ids.size() + 1);
                rows.add(new Object[] { ids.get(key), name, city });
            }
        }
        jdbc.batchUpdate("INSERT INTO stadium (id, name, city) VALUES (?, ?, ?)", rows);
        return ids;
    }

    private Map<String, Integer> insertReferees(JsonNode matches) {
        Map<String, Integer> ids = new HashMap<>();
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode m : matches) {
            JsonNode referee = m.path("referee");
            if (referee.isNull() || referee.isMissingNode()) {
                continue;
            }
            String name = referee.asString();
            if (!ids.containsKey(name)) {
                ids.put(name, ids.size() + 1);
                rows.add(new Object[] { ids.get(name), name });
            }
        }
        jdbc.batchUpdate("INSERT INTO referee (id, name, country_code) VALUES (?, ?, NULL)", rows);
        return ids;
    }

    private static String stadiumKey(String name, String city) {
        return name + "\u0000" + city;
    }

    // Matches --------------------------------------------------------------------

    private void insertMatches(JsonNode matches, Map<String, Integer> stadiums, Map<String, Integer> referees) {
        int competitionId = jdbc.queryForObject("SELECT id FROM competition", Integer.class);
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode m : matches) {
            JsonNode score = m.path("score");
            JsonNode referee = m.path("referee");
            rows.add(new Object[] {
                    requiredInt(m, "id", "match"),
                    competitionId,
                    requiredText(m, "stage", "match"),
                    text(m.path("group")),
                    OffsetDateTime.parse(requiredText(m, "kickoffAt", "match")),
                    stadiums.get(stadiumKey(requiredText(m, "stadium", "match"), requiredText(m, "city", "match"))),
                    integer(m.path("attendance")),
                    referee.isNull() || referee.isMissingNode() ? null : referees.get(referee.asString()),
                    requiredInt(m, "team1Id", "match"),
                    requiredInt(m, "team2Id", "match"),
                    pair(score.path("halfTime"), 0), pair(score.path("halfTime"), 1),
                    pair(score.path("fullTime"), 0), pair(score.path("fullTime"), 1),
                    pair(score.path("extraTime"), 0), pair(score.path("extraTime"), 1),
                    pair(score.path("penalties"), 0), pair(score.path("penalties"), 1) });
        }
        jdbc.batchUpdate("INSERT INTO match (id, competition_id, stage, group_name, kickoff_at, stadium_id, "
                + "attendance, referee_id, team1_id, team2_id, ht_team1, ht_team2, ft_team1, ft_team2, "
                + "et_team1, et_team2, pen_team1, pen_team2) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", rows);
    }

    private void insertLineups(JsonNode matches) {
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode m : matches) {
            for (JsonNode l : m.path("lineups")) {
                rows.add(new Object[] { requiredInt(m, "id", "match"), requiredInt(l, "playerId", "lineup"),
                        requiredInt(l, "teamId", "lineup"), requiredBoolean(l, "starter", "lineup"),
                        requiredBoolean(l, "captain", "lineup") });
            }
        }
        jdbc.batchUpdate("INSERT INTO match_lineup (match_id, player_id, team_id, is_starter, is_captain) "
                + "VALUES (?, ?, ?, ?, ?)", rows);
    }

    private void insertSubstitutions(JsonNode matches) {
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode m : matches) {
            for (JsonNode s : m.path("substitutions")) {
                rows.add(new Object[] { requiredInt(m, "id", "match"), requiredInt(s, "teamId", "substitution"),
                        requiredInt(s, "playerInId", "substitution"), requiredInt(s, "playerOutId", "substitution"),
                        requiredInt(s, "minute", "substitution"), integer(s.path("addedTime")) });
            }
        }
        jdbc.batchUpdate("INSERT INTO substitution (match_id, team_id, player_in_id, player_out_id, minute, "
                + "added_time) VALUES (?, ?, ?, ?, ?, ?)", rows);
    }

    private void insertGoals(JsonNode matches) {
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode m : matches) {
            for (JsonNode g : m.path("goals")) {
                rows.add(new Object[] { requiredInt(m, "id", "match"), requiredInt(g, "teamId", "goal"),
                        requiredInt(g, "playerId", "goal"), requiredInt(g, "minute", "goal"), integer(g.path("addedTime")),
                        requiredBoolean(g, "penalty", "goal"), requiredBoolean(g, "ownGoal", "goal") });
            }
        }
        jdbc.batchUpdate("INSERT INTO goal (match_id, team_id, player_id, minute, added_time, is_penalty, "
                + "is_own_goal) VALUES (?, ?, ?, ?, ?, ?, ?)", rows);
    }

    private void insertBookings(JsonNode matches) {
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode m : matches) {
            for (JsonNode b : m.path("bookings")) {
                rows.add(new Object[] { requiredInt(m, "id", "match"), requiredInt(b, "teamId", "booking"),
                        requiredInt(b, "playerId", "booking"), requiredText(b, "card", "booking"), requiredInt(b, "minute", "booking"),
                        integer(b.path("addedTime")) });
            }
        }
        jdbc.batchUpdate("INSERT INTO booking (match_id, team_id, player_id, card, minute, added_time) "
                + "VALUES (?, ?, ?, ?, ?, ?)", rows);
    }

    private void insertPenaltyKicks(JsonNode matches) {
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode m : matches) {
            for (JsonNode k : m.path("penaltyShootout")) {
                rows.add(new Object[] { requiredInt(m, "id", "match"), requiredInt(k, "order", "penalty kick"),
                        requiredInt(k, "teamId", "penalty kick"), requiredInt(k, "playerId", "penalty kick"), requiredBoolean(k, "scored", "penalty kick") });
            }
        }
        jdbc.batchUpdate("INSERT INTO penalty_kick (match_id, kick_order, team_id, player_id, scored) "
                + "VALUES (?, ?, ?, ?, ?)", rows);
    }

    // Ratings and statistics -----------------------------------------------------

    private void insertPlayerRatings(JsonNode matches) {
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode m : matches) {
            for (JsonNode s : m.path("playerStats")) {
                rows.add(new Object[] { requiredInt(m, "id", "match"), requiredInt(s, "playerId", "player stats"),
                        requiredInt(s, "teamId", "player stats"), decimal(s.path("rating")), integer(s.path("minutesPlayed")),
                        integer(s.path("goals")), integer(s.path("penaltyGoals")), integer(s.path("ownGoals")),
                        integer(s.path("decisiveGoals")), integer(s.path("goalsForOnPitch")),
                        integer(s.path("goalsAgainstOnPitch")), integer(s.path("yellowCards")),
                        requiredBoolean(s, "sentOff", "player stats"), integer(s.path("shootoutScored")),
                        integer(s.path("shootoutMissed")), ALGORITHM_VERSION });
            }
        }
        jdbc.batchUpdate("INSERT INTO player_match_rating (match_id, player_id, team_id, rating, minutes_played, "
                + "goals, penalty_goals, own_goals, decisive_goals, goals_for_on_pitch, goals_against_on_pitch, "
                + "yellow_cards, sent_off, shootout_scored, shootout_missed, algorithm_version) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", rows);
    }

    private void insertTeamRatings(JsonNode matches) {
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode m : matches) {
            for (JsonNode s : m.path("teamStats")) {
                JsonNode wonShootout = s.path("wonShootout");
                rows.add(new Object[] { requiredInt(m, "id", "match"), requiredInt(s, "teamId", "team stats"),
                        decimal(s.path("rating")), requiredText(s, "result", "team stats"), integer(s.path("points")),
                        integer(s.path("goalsFor")), integer(s.path("goalsAgainst")),
                        wonShootout.isNull() || wonShootout.isMissingNode() ? null : wonShootout.booleanValue(),
                        integer(s.path("fairPlayPoints")), ALGORITHM_VERSION });
            }
        }
        jdbc.batchUpdate("INSERT INTO team_match_rating (match_id, team_id, rating, result, points, goals_for, "
                + "goals_against, won_shootout, fair_play_points, algorithm_version) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", rows);
    }

    private void insertPlayerStats(JsonNode matches) {
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode m : matches) {
            for (JsonNode s : m.path("playerStats")) {
                rows.add(new Object[] { requiredInt(m, "id", "match"), requiredInt(s, "playerId", "player stats"),
                        requiredInt(s, "teamId", "player stats"), integer(s.path("foulsCommitted")),
                        integer(s.path("freeKicksWon")), integer(s.path("freeKicksConceded")),
                        integer(s.path("duelsWon")), integer(s.path("duelsLost")), integer(s.path("corners")),
                        integer(s.path("offsides")), decimal(s.path("distanceKm")),
                        requiredBoolean(s, "manOfTheMatch", "player stats"), requiredBoolean(s, "goalkeeper", "player stats") });
            }
        }
        jdbc.batchUpdate("INSERT INTO player_match_stats (match_id, player_id, team_id, fouls_committed, "
                + "free_kicks_won, free_kicks_conceded, duels_won, duels_lost, corners, offsides, distance_km, "
                + "is_man_of_the_match, is_goalkeeper) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", rows);
    }

    private void insertTeamStats(JsonNode matches) {
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode m : matches) {
            for (JsonNode s : m.path("teamStats")) {
                rows.add(new Object[] { requiredInt(m, "id", "match"), requiredInt(s, "teamId", "team stats"),
                        integer(s.path("foulsCommitted")), integer(s.path("freeKicksWon")),
                        integer(s.path("freeKicksConceded")), integer(s.path("duelsWon")),
                        integer(s.path("duelsLost")), integer(s.path("corners")), integer(s.path("offsides")),
                        decimal(s.path("distanceKm")) });
            }
        }
        jdbc.batchUpdate("INSERT INTO team_match_stats (match_id, team_id, fouls_committed, free_kicks_won, "
                + "free_kicks_conceded, duels_won, duels_lost, corners, offsides, distance_km) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", rows);
    }

    // Helpers --------------------------------------------------------------------

    private static boolean isAbsent(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode();
    }

    private static JsonNode required(JsonNode node, String field, String context) {
        JsonNode value = node.path(field);
        if (isAbsent(value)) {
            throw new IllegalStateException("Missing field '" + field + "' in " + context);
        }
        return value;
    }

    private static int requiredInt(JsonNode node, String field, String context) {
        return required(node, field, context).intValue();
    }

    private static boolean requiredBoolean(JsonNode node, String field, String context) {
        return required(node, field, context).booleanValue();
    }

    private static String requiredText(JsonNode node, String field, String context) {
        return required(node, field, context).asString();
    }

    private static String text(JsonNode node) {
        return isAbsent(node) ? null : node.asString();
    }

    private static Integer integer(JsonNode node) {
        return isAbsent(node) ? null : node.intValue();
    }

    private static BigDecimal decimal(JsonNode node) {
        return isAbsent(node) ? null : node.decimalValue();
    }

    /** Element {@code index} of a two-element array, or null when the array is absent or null. */
    private static Integer pair(JsonNode array, int index) {
        return isAbsent(array) ? null : integer(array.path(index));
    }
}
