package stats_foot.stats_foot.database;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** Runs {@code db/schema.sql} on a plain in-memory H2 database (no Spring context). */
class SchemaScriptTest {

    /** Same options as the application URL: see the header of schema.sql. */
    static final String URL_OPTIONS = "MODE=PostgreSQL;NON_KEYWORDS=MINUTE,VALUE;DB_CLOSE_DELAY=-1";

    private static final Map<String, List<String>> TABLES = new LinkedHashMap<>();

    static {
        TABLES.put("competition", List.of("id", "name"));
        TABLES.put("team", List.of("id", "name", "fifa_code"));
        TABLES.put("stadium", List.of("id", "name", "city"));
        TABLES.put("referee", List.of("id", "name", "country_code"));
        TABLES.put("player", List.of("id", "name", "team_id"));
        TABLES.put("match", List.of("id", "competition_id", "stage", "group_name", "kickoff_at", "stadium_id",
                "attendance", "referee_id", "team1_id", "team2_id", "ht_team1", "ht_team2", "ft_team1", "ft_team2",
                "et_team1", "et_team2", "pen_team1", "pen_team2"));
        TABLES.put("match_lineup", List.of("match_id", "player_id", "team_id", "is_starter", "is_captain"));
        TABLES.put("substitution", List.of("id", "match_id", "team_id", "player_in_id", "player_out_id", "minute",
                "added_time"));
        TABLES.put("goal", List.of("id", "match_id", "team_id", "player_id", "minute", "added_time", "is_penalty",
                "is_own_goal"));
        TABLES.put("booking", List.of("id", "match_id", "team_id", "player_id", "card", "minute", "added_time"));
        TABLES.put("penalty_kick", List.of("match_id", "kick_order", "team_id", "player_id", "scored"));
        TABLES.put("player_match_rating", List.of("match_id", "player_id", "team_id", "rating", "minutes_played",
                "goals", "penalty_goals", "own_goals", "decisive_goals", "goals_for_on_pitch",
                "goals_against_on_pitch", "yellow_cards", "sent_off", "shootout_scored", "shootout_missed",
                "algorithm_version", "computed_at"));
        TABLES.put("team_match_rating", List.of("match_id", "team_id", "rating", "result", "points", "goals_for",
                "goals_against", "won_shootout", "fair_play_points", "algorithm_version", "computed_at"));
        TABLES.put("player_match_stats", List.of("match_id", "player_id", "team_id", "fouls_committed",
                "free_kicks_won", "free_kicks_conceded", "duels_won", "duels_lost", "corners", "offsides",
                "distance_km", "is_man_of_the_match", "is_goalkeeper"));
        TABLES.put("team_match_stats", List.of("match_id", "team_id", "fouls_committed", "free_kicks_won",
                "free_kicks_conceded", "duels_won", "duels_lost", "corners", "offsides", "distance_km"));
    }

    private static final List<String> VIEW_COLUMNS = List.of("competition_id", "player_id", "player", "team_id",
            "team", "matches_played", "matches_started", "minutes_played", "goals", "penalty_goals", "own_goals",
            "yellow_cards", "red_cards", "fouls_committed", "free_kicks_won", "free_kicks_conceded", "duels_won",
            "duels_lost", "corners", "offsides", "distance_km", "man_of_the_match_count", "man_of_the_match",
            "detailed_stats_matches", "rating", "rating_weighted", "goalkeeper_matches", "captain_matches");

    private DataSource dataSource;

    @BeforeEach
    void createDatabase() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:schema" + UUID.randomUUID().toString().replace("-", "") + ";" + URL_OPTIONS);
        dataSource = ds;
    }

    private void runScript() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql"));
        populator.execute(dataSource);
    }

    private Set<String> columnsOf(String table) throws SQLException {
        Set<String> columns = new TreeSet<>();
        try (Connection c = dataSource.getConnection();
                var ps = c.prepareStatement("SELECT LOWER(COLUMN_NAME) FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE LOWER(TABLE_NAME) = ? AND TABLE_SCHEMA = 'PUBLIC'")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString(1));
                }
            }
        }
        return columns;
    }

    private void execute(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    @Test
    void scriptRunsTwiceWithoutError() {
        assertDoesNotThrow(this::runScript);
        assertDoesNotThrow(this::runScript);
    }

    @Test
    void everyTableExistsWithItsColumns() throws SQLException {
        runScript();
        runScript();
        assertEquals(15, TABLES.size());
        for (Map.Entry<String, List<String>> table : TABLES.entrySet()) {
            Set<String> expected = new TreeSet<>(table.getValue());
            assertEquals(expected, columnsOf(table.getKey()), "columns of " + table.getKey());
        }
    }

    @Test
    void viewExistsWithItsColumns() throws SQLException {
        runScript();
        List<String> views = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT LOWER(TABLE_NAME) FROM INFORMATION_SCHEMA.VIEWS "
                        + "WHERE TABLE_SCHEMA = 'PUBLIC'")) {
            while (rs.next()) {
                views.add(rs.getString(1));
            }
        }
        assertEquals(List.of("v_player_tournament_stats"), views);
        assertEquals(new TreeSet<>(VIEW_COLUMNS), columnsOf("v_player_tournament_stats"));
    }

    @Test
    void explicitIdentifiersAreAcceptedAndIdentityCanContinue() throws SQLException {
        runScript();
        execute("INSERT INTO team (id, name, fifa_code) VALUES (10, 'Mexico', 'MEX')");
        execute("ALTER TABLE team ALTER COLUMN id RESTART WITH 11");
        execute("INSERT INTO team (name, fifa_code) VALUES ('Canada', 'CAN')");
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT id FROM team WHERE name = 'Canada'")) {
            assertTrue(rs.next());
            assertEquals(11, rs.getInt(1));
        }
    }

    @Test
    void reservedWordColumnsAreUsable() throws SQLException {
        runScript();
        execute("INSERT INTO team (id, name, fifa_code) VALUES (1, 'A', 'AAA'), (2, 'B', 'BBB')");
        execute("INSERT INTO competition (id, name) VALUES (1, 'WC')");
        execute("INSERT INTO stadium (id, name, city) VALUES (1, 'S', 'C')");
        execute("INSERT INTO player (id, name, team_id) VALUES (1, 'P1', 1)");
        execute("INSERT INTO player (id, name, team_id) VALUES (2, 'P2', 1)");
        execute("INSERT INTO match (id, competition_id, stage, kickoff_at, stadium_id, team1_id, team2_id) "
                + "VALUES (1, 1, 'GROUP', TIMESTAMP WITH TIME ZONE '2026-06-11 19:00:00-06:00', 1, 1, 2)");
        execute("INSERT INTO substitution (match_id, team_id, player_in_id, player_out_id, minute) "
                + "VALUES (1, 1, 2, 1, 60)");
    }

    @Test
    void checkConstraintsStillWorkFromAnotherConnection() throws SQLException {
        runScript();
        execute("INSERT INTO team (id, name, fifa_code) VALUES (1, 'A', 'AAA'), (2, 'B', 'BBB')");
        execute("INSERT INTO competition (id, name) VALUES (1, 'WC')");
        execute("INSERT INTO stadium (id, name, city) VALUES (1, 'S', 'C')");
        String insert = "INSERT INTO match (id, competition_id, stage, kickoff_at, stadium_id, team1_id, team2_id) "
                + "VALUES (1, 1, '%s', TIMESTAMP WITH TIME ZONE '2026-06-11 19:00:00-06:00', 1, 1, 2)";
        assertThrows(SQLException.class, () -> execute(String.format(insert, "UNKNOWN")));
        assertDoesNotThrow(() -> execute(String.format(insert, "FINAL")));
    }

    @Test
    void viewIsQueryableOnEmptyTables() throws SQLException {
        runScript();
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT * FROM v_player_tournament_stats")) {
            assertFalse(rs.next());
        }
    }

    @Test
    void viewComputesTotalsWithoutMultiplyingRows() throws SQLException {
        runScript();
        execute("INSERT INTO competition (id, name) VALUES (1, 'WC')");
        execute("INSERT INTO team (id, name, fifa_code) VALUES (1, 'A', 'AAA'), (2, 'B', 'BBB')");
        execute("INSERT INTO stadium (id, name, city) VALUES (1, 'S', 'C')");
        execute("INSERT INTO player (id, name, team_id) VALUES (1, 'Keeper', 1)");
        execute("INSERT INTO match (id, competition_id, stage, kickoff_at, stadium_id, team1_id, team2_id) VALUES "
                + "(1, 1, 'GROUP', TIMESTAMP WITH TIME ZONE '2026-06-11 19:00:00-06:00', 1, 1, 2), "
                + "(2, 1, 'GROUP', TIMESTAMP WITH TIME ZONE '2026-06-15 19:00:00-06:00', 1, 1, 2)");
        execute("INSERT INTO match_lineup VALUES (1, 1, 1, TRUE, TRUE), (2, 1, 1, TRUE, FALSE)");
        for (int m = 1; m <= 2; m++) {
            execute("INSERT INTO player_match_rating (match_id, player_id, team_id, rating, minutes_played, goals, "
                    + "penalty_goals, own_goals, decisive_goals, goals_for_on_pitch, goals_against_on_pitch, "
                    + "yellow_cards, sent_off, shootout_scored, shootout_missed, algorithm_version) VALUES ("
                    + m + ", 1, 1, 7.0, 90, 0, 0, 0, 0, 0, 0, 0, FALSE, 0, 0, 'v1')");
        }
        execute("INSERT INTO player_match_stats (match_id, player_id, team_id, is_goalkeeper) "
                + "VALUES (1, 1, 1, TRUE)");
        execute("INSERT INTO booking (match_id, team_id, player_id, card, minute) VALUES "
                + "(1, 1, 1, 'Y', 10), (1, 1, 1, 'Y/R', 80), (2, 1, 1, 'R', 5)");
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT matches_played, matches_started, minutes_played, yellow_cards, "
                        + "red_cards, goalkeeper_matches, captain_matches, detailed_stats_matches "
                        + "FROM v_player_tournament_stats")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt("matches_played"));
            assertEquals(2, rs.getInt("matches_started"));
            assertEquals(180, rs.getInt("minutes_played"));
            assertEquals(1, rs.getInt("yellow_cards"));
            assertEquals(2, rs.getInt("red_cards"));
            assertEquals(1, rs.getInt("goalkeeper_matches"));
            assertEquals(1, rs.getInt("captain_matches"));
            assertEquals(1, rs.getInt("detailed_stats_matches"));
            assertFalse(rs.next());
        }
    }
}
