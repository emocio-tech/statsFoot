package stats_foot.stats_foot.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
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

/** The view v_team_tournament_stats computed from the imported rows equals the file's tournament blocks. */
class TeamTournamentStatsViewTest {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private static JsonNode root;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void importFile() {
        root = JsonMapper.builder().build().readTree(DataFileImporterTest.DATA_FILE.toFile());
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:teamview" + UUID.randomUUID().toString().replace("-", "") + ";"
                + SchemaScriptTest.URL_OPTIONS);
        new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds);
        new DataFileImporter(jdbc, DataFileImporterTest.DATA_FILE).importFile();
    }

    @Test
    void viewEqualsTheTournamentBlockOfEveryTeam() {
        int checked = 0;
        for (JsonNode team : root.path("teams")) {
            int id = team.path("id").intValue();
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM v_team_tournament_stats WHERE team_id = ?", id);
            assertEquals(1, rows.size(), "view rows of team " + id);
            Map<String, Object> row = rows.get(0);

            JsonNode tournament = team.path("tournament");
            for (String column : tournament.propertyNames()) {
                assertTrue(row.containsKey(column.toUpperCase()), "view column " + column);
                JsonNode expected = tournament.path(column);
                Object actual = row.get(column.toUpperCase());
                String label = "team " + id + " " + column;
                if (expected.isNull()) {
                    assertNull(actual, label + " should be NULL");
                } else if (expected.isNumber()) {
                    assertNotNull(actual, label + " should not be NULL (expected " + expected + ")");
                    BigDecimal diff = expected.decimalValue().subtract(new BigDecimal(actual.toString())).abs();
                    assertTrue(diff.compareTo(TOLERANCE) <= 0, label + ": expected " + expected + " but was " + actual);
                } else {
                    assertEquals(expected.asString(), String.valueOf(actual), label);
                }
            }
            checked++;
        }
        assertEquals(48, checked);
        assertEquals(48, jdbc.queryForObject("SELECT COUNT(*) FROM v_team_tournament_stats", Integer.class));
    }

}
