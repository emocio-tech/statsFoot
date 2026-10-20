package stats_foot.stats_foot.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Imports the real data file into a plain in-memory H2 database (no Spring context). */
class DataFileImporterTest {

    static final Path DATA_FILE = Path.of("data", "donneesStats.json");

    private static JsonNode root;

    private JdbcTemplate jdbc;

    @BeforeAll
    static void readFile() {
        root = JsonMapper.builder().build().readTree(DATA_FILE.toFile());
    }

    @BeforeEach
    void createDatabase() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:importer" + UUID.randomUUID().toString().replace("-", "") + ";"
                + SchemaScriptTest.URL_OPTIONS);
        new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds);
    }

    private void importDefaultFile() {
        new DataFileImporter(jdbc, DATA_FILE).importFile();
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static int sizeOfAll(String arrayName) {
        int total = 0;
        for (JsonNode match : root.path("matches")) {
            JsonNode array = match.path(arrayName);
            total += array.isArray() ? array.size() : 0;
        }
        return total;
    }

    private static JsonNode player(int id) {
        for (JsonNode p : root.path("players")) {
            if (p.path("id").intValue() == id) {
                return p;
            }
        }
        throw new IllegalArgumentException("No player " + id);
    }

    @Test
    void everyTableHasTheRowCountOfTheFile() {
        importDefaultFile();

        Set<String> stadiums = new HashSet<>();
        Set<String> referees = new HashSet<>();
        for (JsonNode m : root.path("matches")) {
            stadiums.add(m.path("stadium").asString() + "|" + m.path("city").asString());
            referees.add(m.path("referee").asString());
        }

        assertEquals(1, count("competition"));
        assertEquals(root.path("teams").size(), count("team"));
        assertEquals(root.path("players").size(), count("player"));
        assertEquals(root.path("matches").size(), count("match"));
        assertEquals(stadiums.size(), count("stadium"));
        assertEquals(referees.size(), count("referee"));
        assertEquals(sizeOfAll("lineups"), count("match_lineup"));
        assertEquals(sizeOfAll("substitutions"), count("substitution"));
        assertEquals(sizeOfAll("goals"), count("goal"));
        assertEquals(sizeOfAll("bookings"), count("booking"));
        assertEquals(sizeOfAll("penaltyShootout"), count("penalty_kick"));
        assertEquals(sizeOfAll("playerStats"), count("player_match_rating"));
        assertEquals(sizeOfAll("playerStats"), count("player_match_stats"));
        assertEquals(sizeOfAll("teamStats"), count("team_match_rating"));
        assertEquals(sizeOfAll("teamStats"), count("team_match_stats"));

        // Reference totals of the file.
        assertEquals(48, count("team"));
        assertEquals(1039, count("player"));
        assertEquals(104, count("match"));
        assertEquals(16, count("stadium"));
        assertEquals(42, count("referee"));
        assertEquals(3288, count("match_lineup"));
        assertEquals(1000, count("substitution"));
        assertEquals(308, count("goal"));
        assertEquals(282, count("booking"));
        assertEquals(40, count("penalty_kick"));
        assertEquals(3288, count("player_match_rating"));
        assertEquals(208, count("team_match_stats"));
    }

    @Test
    void matchRowsAndMinutesOfReferencePlayersEqualTheFile() {
        importDefaultFile();
        for (int playerId : new int[] { 1, 2, 316 }) {
            int expectedRows = 0;
            for (JsonNode m : root.path("matches")) {
                for (JsonNode s : m.path("playerStats")) {
                    if (s.path("playerId").intValue() == playerId) {
                        expectedRows++;
                    }
                }
            }
            String where = " WHERE player_id = " + playerId;
            assertEquals(expectedRows, count("player_match_rating" + where), "rating rows of " + playerId);
            assertEquals(expectedRows, count("player_match_stats" + where), "stats rows of " + playerId);
            Integer minutes = jdbc.queryForObject(
                    "SELECT SUM(minutes_played) FROM player_match_rating" + where, Integer.class);
            assertEquals(player(playerId).path("tournament").path("minutes_played").intValue(),
                    minutes == null ? 0 : minutes, "minutes of " + playerId);
        }
        assertEquals(438, jdbc.queryForObject(
                "SELECT SUM(minutes_played) FROM player_match_rating WHERE player_id = 1", Integer.class));
        assertEquals("Raul RANGEL", jdbc.queryForObject("SELECT name FROM player WHERE id = 1", String.class));
        assertEquals("MEX", jdbc.queryForObject(
                "SELECT t.fifa_code FROM team t JOIN player p ON p.team_id = t.id WHERE p.id = 1", String.class));
        assertEquals(5, count("player_match_stats WHERE player_id = 1 AND is_goalkeeper"));
        assertEquals(3, count("match_lineup WHERE player_id = 2 AND is_captain"));
        assertEquals(2, count("player_match_rating WHERE player_id = 316"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT SUM(minutes_played) FROM player_match_rating WHERE player_id = 316", Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT SUM(goals) FROM player_match_rating WHERE player_id = 1", Integer.class));
    }

    @Test
    void exactlyOneManOfTheMatchPerMatch() {
        importDefaultFile();
        assertEquals(104, count("match"));
        assertEquals(104, count("player_match_stats WHERE is_man_of_the_match"));
        List<Map<String, Object>> wrong = jdbc.queryForList("SELECT match_id, COUNT(*) AS n FROM player_match_stats "
                + "WHERE is_man_of_the_match GROUP BY match_id HAVING COUNT(*) <> 1");
        assertTrue(wrong.isEmpty(), "matches without exactly one man of the match: " + wrong);
        assertEquals(104, count("(SELECT DISTINCT match_id FROM player_match_stats WHERE is_man_of_the_match) x"));
    }

    @Test
    void identifiersAreThoseOfTheFile() {
        importDefaultFile();
        for (JsonNode t : root.path("teams")) {
            assertEquals(t.path("name").asString(), jdbc.queryForObject("SELECT name FROM team WHERE id = ?",
                    String.class, t.path("id").intValue()));
        }
        for (JsonNode p : root.path("players")) {
            assertEquals(p.path("teamId").intValue(), jdbc.queryForObject(
                    "SELECT team_id FROM player WHERE id = ?", Integer.class, p.path("id").intValue()));
        }
        for (JsonNode m : root.path("matches")) {
            assertEquals(m.path("team1Id").intValue(), jdbc.queryForObject(
                    "SELECT team1_id FROM match WHERE id = ?", Integer.class, m.path("id").intValue()));
        }
        assertEquals(root.path("competition").path("name").asString(),
                jdbc.queryForObject("SELECT name FROM competition WHERE id = ?", String.class,
                        root.path("competition").path("id").intValue()));
    }

    @Test
    void insertionWithoutIdentifierAfterTheImportGetsANewIdentifier() {
        importDefaultFile();
        new DataFileImporter(jdbc, DATA_FILE).restartIdentities();
        int maxTeam = jdbc.queryForObject("SELECT MAX(id) FROM team", Integer.class);
        int maxPlayer = jdbc.queryForObject("SELECT MAX(id) FROM player", Integer.class);
        int maxMatch = jdbc.queryForObject("SELECT MAX(id) FROM match", Integer.class);

        jdbc.update("INSERT INTO team (name, fifa_code) VALUES ('New team', 'NEW')");
        int newTeam = jdbc.queryForObject("SELECT id FROM team WHERE name = 'New team'", Integer.class);
        assertEquals(maxTeam + 1, newTeam);

        jdbc.update("INSERT INTO player (name, team_id) VALUES ('New player', ?)", newTeam);
        assertEquals(maxPlayer + 1,
                jdbc.queryForObject("SELECT id FROM player WHERE name = 'New player'", Integer.class));

        jdbc.update("INSERT INTO match (competition_id, stage, kickoff_at, stadium_id, team1_id, team2_id) "
                + "VALUES (?, 'GROUP', TIMESTAMP WITH TIME ZONE '2026-07-01 12:00:00+00:00', "
                + "(SELECT MIN(id) FROM stadium), ?, ?)",
                root.path("competition").path("id").intValue(), newTeam, maxTeam);
        assertEquals(maxMatch + 1, jdbc.queryForObject("SELECT MAX(id) FROM match", Integer.class));

        int maxStadium = jdbc.queryForObject("SELECT MAX(id) FROM stadium", Integer.class);
        jdbc.update("INSERT INTO stadium (name, city) VALUES ('New stadium', 'New city')");
        assertEquals(maxStadium + 1,
                jdbc.queryForObject("SELECT id FROM stadium WHERE name = 'New stadium'", Integer.class));
        for (String table : List.of("substitution", "goal", "booking")) {
            int max = jdbc.queryForObject("SELECT MAX(id) FROM " + table, Integer.class);
            int matchId = maxMatch + 1;
            int playerId = maxPlayer + 1;
            String columns = switch (table) {
                case "substitution" -> "(match_id, team_id, player_in_id, player_out_id, minute) VALUES ("
                        + matchId + ", " + newTeam + ", " + playerId + ", " + playerId + ", 1)";
                case "goal" -> "(match_id, team_id, player_id, minute) VALUES (" + matchId + ", " + newTeam + ", "
                        + playerId + ", 1)";
                default -> "(match_id, team_id, player_id, card, minute) VALUES (" + matchId + ", " + newTeam + ", "
                        + playerId + ", 'Y', 1)";
            };
            jdbc.update("INSERT INTO " + table + " " + columns);
            assertEquals(max + 1, jdbc.queryForObject("SELECT MAX(id) FROM " + table, Integer.class), table);
        }
    }

    @Test
    void anotherCopyOfTheFileInATempDirWorks(@TempDir Path dir) throws IOException {
        Path copy = Files.copy(DATA_FILE, dir.resolve("copy.json"));
        new DataFileImporter(jdbc, copy).importFile();
        assertEquals(1039, count("player"));
        assertEquals(104, count("match"));
    }

    @Test
    void penaltyScoreIsFilledOnlyForMatchesWithAShootout() {
        importDefaultFile();
        int withShootout = 0;
        for (JsonNode m : root.path("matches")) {
            JsonNode penalties = m.path("score").path("penalties");
            int id = m.path("id").intValue();
            Integer pen1 = jdbc.queryForObject("SELECT pen_team1 FROM match WHERE id = ?", Integer.class, id);
            Integer pen2 = jdbc.queryForObject("SELECT pen_team2 FROM match WHERE id = ?", Integer.class, id);
            if (penalties.isArray()) {
                withShootout++;
                assertNotNull(pen1, "pen_team1 of match " + id);
                assertNotNull(pen2, "pen_team2 of match " + id);
                assertEquals(penalties.get(0).intValue(), pen1);
                assertEquals(penalties.get(1).intValue(), pen2);
            } else {
                assertNull(pen1, "pen_team1 of match " + id);
                assertNull(pen2, "pen_team2 of match " + id);
            }
        }
        assertTrue(withShootout > 0, "the file has at least one shootout");
        assertEquals(withShootout, count("match WHERE pen_team1 IS NOT NULL"));
    }

    @Test
    void restartIdentitiesIsIdempotent() {
        importDefaultFile();
        DataFileImporter importer = new DataFileImporter(jdbc, DATA_FILE);
        importer.restartIdentities();
        importer.restartIdentities();

        int maxTeam = jdbc.queryForObject("SELECT MAX(id) FROM team", Integer.class);
        jdbc.update("INSERT INTO team (name, fifa_code) VALUES ('New team', 'NEW')");
        assertEquals(maxTeam + 1, jdbc.queryForObject("SELECT MAX(id) FROM team", Integer.class));
    }

    /** Copy of the file where the first goal lacks the given field. */
    private Path copyWithFirstGoalWithout(Path dir, String field) throws IOException {
        JsonNode copy = JsonMapper.builder().build().readTree(DATA_FILE.toFile());
        for (JsonNode m : copy.path("matches")) {
            if (m.path("goals").size() > 0) {
                ((ObjectNode) m.path("goals").get(0)).remove(field);
                break;
            }
        }
        Path target = dir.resolve("missing-field.json");
        JsonMapper.builder().build().writeValue(target.toFile(), copy);
        return target;
    }

    @Test
    void missingGoalPlayerIdFailsTheImportWithANeutralMessage(@TempDir Path dir) throws IOException {
        Path copy = copyWithFirstGoalWithout(dir, "playerId");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new DataFileImporter(jdbc, copy).importFile());

        assertEquals("Missing field 'playerId' in goal", e.getMessage());
        assertFalse(e.getMessage().contains("missing-field.json"));
    }

    @Test
    void missingGoalMinuteFailsTheImport(@TempDir Path dir) throws IOException {
        Path copy = copyWithFirstGoalWithout(dir, "minute");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new DataFileImporter(jdbc, copy).importFile());

        assertEquals("Missing field 'minute' in goal", e.getMessage());
    }
}
