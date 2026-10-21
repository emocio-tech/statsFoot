package stats_foot.stats_foot.database;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

/** Start-up initializer without Spring context, on in-memory H2 databases. */
class DatabaseInitializerTest {

    static final Path DATA_FILE = DataFileImporterTest.DATA_FILE;

    private static final List<String> TABLES = List.of("competition", "team", "stadium", "referee", "player",
            "match", "match_lineup", "substitution", "goal", "booking", "penalty_kick", "player_match_rating",
            "team_match_rating", "player_match_stats", "team_match_stats");

    @TempDir
    Path tempDir;

    private final DatabaseStatus status = new DatabaseStatus();

    private static DataSource memoryDataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:init" + UUID.randomUUID().toString().replace("-", "") + ";"
                + SchemaScriptTest.URL_OPTIONS);
        return ds;
    }

    private static int count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static void assertFullyImported(JdbcTemplate jdbc) {
        assertEquals(48, count(jdbc, "team"));
        assertEquals(1039, count(jdbc, "player"));
        assertEquals(104, count(jdbc, "match"));
    }

    private static void assertAllTablesEmpty(JdbcTemplate jdbc) {
        for (String table : TABLES) {
            assertEquals(0, count(jdbc, table), table);
        }
    }

    @Test
    void emptyDatabaseIsImported() {
        DataSource ds = memoryDataSource();

        new DatabaseInitializer(ds, DATA_FILE, status).initialize();

        assertFalse(status.isFailed());
        assertFullyImported(new JdbcTemplate(ds));
    }

    @Test
    void secondRunOnFilledDatabaseImportsNothing() {
        DataSource ds = memoryDataSource();
        DatabaseInitializer initializer = new DatabaseInitializer(ds, DATA_FILE, status);
        initializer.initialize();

        assertDoesNotThrow(initializer::initialize);

        assertFalse(status.isFailed());
        assertFullyImported(new JdbcTemplate(ds));
    }

    @Test
    void truncatedFileLeavesNoRowAndNextRunWithCompleteFileRecovers() throws IOException {
        byte[] all = Files.readAllBytes(DATA_FILE);
        Path truncated = tempDir.resolve("truncated.json");
        Files.write(truncated, java.util.Arrays.copyOf(all, all.length / 2));
        DataSource ds = memoryDataSource();

        assertDoesNotThrow(() -> new DatabaseInitializer(ds, truncated, status).initialize());

        assertTrue(status.isFailed());
        assertAllTablesEmpty(new JdbcTemplate(ds));

        new DatabaseInitializer(ds, DATA_FILE, status).initialize();

        assertFalse(status.isFailed());
        assertFullyImported(new JdbcTemplate(ds));
    }

    @Test
    void importFailingHalfWayRollsBackEveryTable() throws IOException {
        // Valid JSON whose last section is broken: earlier tables are filled before the failure.
        String json = Files.readString(DATA_FILE);
        int cut = json.lastIndexOf("\"playerStats\"");
        Path broken = tempDir.resolve("broken.json");
        Files.writeString(broken, json.substring(0, cut) + "\"playerStats\": [ {\"playerId\": 999999} ] } ] }");
        DataSource ds = memoryDataSource();

        assertDoesNotThrow(() -> new DatabaseInitializer(ds, broken, status).initialize());

        assertTrue(status.isFailed());
        assertAllTablesEmpty(new JdbcTemplate(ds));
    }

    @Test
    void missingFileStoresFailureWithoutThrowing() {
        DataSource ds = memoryDataSource();

        assertDoesNotThrow(() -> new DatabaseInitializer(ds, tempDir.resolve("absent.json"), status).initialize());

        assertTrue(status.isFailed());
        assertAllTablesEmpty(new JdbcTemplate(ds));
    }

    @Test
    void unreachableDatabaseStoresFailureWithoutThrowing() throws IOException {
        Path ordinaryFile = tempDir.resolve("ordinary.txt");
        Files.writeString(ordinaryFile, "not a database");
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:file:" + ordinaryFile.toAbsolutePath().toString().replace('\\', '/') + "/db;"
                + SchemaScriptTest.URL_OPTIONS);

        assertDoesNotThrow(() -> new DatabaseInitializer(ds, DATA_FILE, status).initialize());

        assertTrue(status.isFailed());
    }

    @Test
    void afterSingletonsInstantiatedDelegatesToInitialize() {
        DataSource ds = memoryDataSource();

        new DatabaseInitializer(ds, DATA_FILE, status).afterSingletonsInstantiated();

        assertFullyImported(new JdbcTemplate(ds));
    }

    @Test
    void missingRequiredFieldRollsBackEveryTableAndFailsTheStatus() throws IOException {
        tools.jackson.databind.JsonNode root = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(DATA_FILE.toFile());
        for (tools.jackson.databind.JsonNode m : root.path("matches")) {
            if (m.path("goals").size() > 0) {
                ((tools.jackson.databind.node.ObjectNode) m.path("goals").get(0)).remove("playerId");
                break;
            }
        }
        Path broken = tempDir.resolve("missing-field.json");
        tools.jackson.databind.json.JsonMapper.builder().build().writeValue(broken.toFile(), root);
        DataSource ds = memoryDataSource();

        assertDoesNotThrow(() -> new DatabaseInitializer(ds, broken, status).initialize());

        assertTrue(status.isFailed());
        assertAllTablesEmpty(new JdbcTemplate(ds));
    }

    private static void assertInsertionWithoutIdentifierWorks(JdbcTemplate jdbc, String name) {
        int max = jdbc.queryForObject("SELECT MAX(id) FROM team", Integer.class);
        jdbc.update("INSERT INTO team (name, fifa_code) VALUES (?, 'NEW')", name);
        assertEquals(max + 1, jdbc.queryForObject("SELECT id FROM team WHERE name = ?", Integer.class, name));
    }

    @Test
    void insertionWithoutIdentifierWorksAfterInitializeAndAfterASecondRun() {
        DataSource ds = memoryDataSource();
        DatabaseInitializer initializer = new DatabaseInitializer(ds, DATA_FILE, status);
        initializer.initialize();
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        assertInsertionWithoutIdentifierWorks(jdbc, "First new team");

        initializer.initialize();

        assertFalse(status.isFailed());
        assertInsertionWithoutIdentifierWorks(jdbc, "Second new team");
    }

    @Test
    void identityRestartWorksOnAFilledDatabaseWhoseSequencesWereReset() {
        DataSource ds = memoryDataSource();
        new DatabaseInitializer(ds, DATA_FILE, status).initialize();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("ALTER TABLE team ALTER COLUMN id RESTART WITH 1");

        new DatabaseInitializer(ds, DATA_FILE, status).initialize();

        assertInsertionWithoutIdentifierWorks(jdbc, "Late new team");
    }
}
