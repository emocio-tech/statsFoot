package stats_foot.stats_foot.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The view v_player_tournament_stats computed from the imported rows equals the file's tournament blocks. */
class PlayerTournamentStatsViewTest {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private static JsonNode root;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void importFile() {
        root = JsonMapper.builder().build().readTree(DataFileImporterTest.DATA_FILE.toFile());
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:view" + UUID.randomUUID().toString().replace("-", "") + ";"
                + SchemaScriptTest.URL_OPTIONS);
        new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds);
        new DataFileImporter(jdbc, DataFileImporterTest.DATA_FILE).importFile();
    }

    private static void assertSameValue(JsonNode expected, Object actual, String label) {
        if (expected == null || expected.isNull()) {
            assertNull(actual, label + " should be NULL");
            return;
        }
        assertNotNull(actual, label + " should not be NULL (expected " + expected + ")");
        if (expected.isBoolean()) {
            assertEquals(expected.booleanValue(), actual, label);
        } else if (expected.isNumber()) {
            BigDecimal diff = expected.decimalValue().subtract(new BigDecimal(actual.toString())).abs();
            assertTrue(diff.compareTo(TOLERANCE) <= 0,
                    label + ": expected " + expected + " but was " + actual);
        } else {
            assertEquals(expected.asString(), actual.toString(), label);
        }
    }

    @Test
    void viewEqualsTheTournamentBlockOfEveryPlayer() {
        int checked = 0;
        List<String> columnsSeen = new ArrayList<>();
        for (JsonNode player : root.path("players")) {
            int id = player.path("id").intValue();
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM v_player_tournament_stats WHERE player_id = ?", id);
            assertEquals(1, rows.size(), "view rows of player " + id);
            Map<String, Object> row = rows.get(0);

            JsonNode tournament = player.path("tournament");
            for (String column : tournament.propertyNames()) {
                assertTrue(row.containsKey(column.toUpperCase()), "view column " + column);
                assertSameValue(tournament.path(column), row.get(column.toUpperCase()),
                        "player " + id + " " + column);
                if (checked == 0) {
                    columnsSeen.add(column);
                }
            }
            assertSameValue(player.path("goalkeeperMatches"), row.get("GOALKEEPER_MATCHES"),
                    "player " + id + " goalkeeper_matches");
            assertSameValue(player.path("captainMatches"), row.get("CAPTAIN_MATCHES"),
                    "player " + id + " captain_matches");
            checked++;
        }
        assertEquals(1039, checked);
        assertEquals(1039, jdbc.queryForObject("SELECT COUNT(*) FROM v_player_tournament_stats", Integer.class));
        assertTrue(columnsSeen.contains("rating_weighted") && columnsSeen.contains("man_of_the_match"));
    }

    @Test
    void weightedRatingIsNullForPlayersWithoutMinutes() {
        Integer nullCount = jdbc.queryForObject("SELECT COUNT(*) FROM v_player_tournament_stats "
                + "WHERE minutes_played = 0 AND rating_weighted IS NULL", Integer.class);
        Integer zeroMinutes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM v_player_tournament_stats WHERE minutes_played = 0", Integer.class);
        assertEquals(zeroMinutes, nullCount);
        assertNull(jdbc.queryForObject(
                "SELECT rating_weighted FROM v_player_tournament_stats WHERE player_id = 316", BigDecimal.class));
    }
}
