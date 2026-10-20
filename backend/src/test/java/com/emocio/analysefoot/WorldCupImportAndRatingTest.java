package com.emocio.analysefoot;

import com.emocio.analysefoot.dto.MatchRatings;
import com.emocio.analysefoot.dto.MatchSummary;
import com.emocio.analysefoot.dto.PlayerRating;
import com.emocio.analysefoot.dto.PlayerTournamentStats;
import com.emocio.analysefoot.dto.TeamRanking;
import com.emocio.analysefoot.dto.TeamTournamentStats;
import com.emocio.analysefoot.importer.WorldCupImporter;
import com.emocio.analysefoot.service.RatingQueryService;
import com.emocio.analysefoot.service.TournamentStatsQueryService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Import de worldcup-sample.json puis calcul des notes avec le script du skill notation-matchs.
 * Nécessite la base de test PostgreSQL (voir {@link PostgresTestSupport}).
 */
@SpringBootTest
@EnabledIf("com.emocio.analysefoot.PostgresTestSupport#isAvailable")
class WorldCupImportAndRatingTest {

    private static final Path RATING_SCRIPT =
            Path.of("..", ".claude", "skills", "notation-matchs", "compute_ratings.sql");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestSupport.register(registry);
    }

    @Autowired
    private Flyway flyway;

    @Autowired
    private WorldCupImporter importer;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RatingQueryService ratingQueryService;

    @Autowired
    private TournamentStatsQueryService tournamentStatsQueryService;

    @BeforeEach
    void resetDatabase() throws Exception {
        flyway.clean();
        flyway.migrate();
        assertThat(importer.importFile(new ClassPathResource("worldcup-sample.json").getFile().toPath())).isTrue();
    }

    @Nested
    class Import {

        @Test
        void importsEveryEntityOfTheFile() {
            assertThat(count("competition")).isEqualTo(1);
            assertThat(count("team")).isEqualTo(3);
            assertThat(count("stadium")).isEqualTo(2);
            assertThat(count("referee")).isEqualTo(1);
            assertThat(count("player")).isEqualTo(10);
            assertThat(count("match")).isEqualTo(2);
            assertThat(count("match_lineup")).isEqualTo(14);
            assertThat(count("substitution")).isEqualTo(2);
            assertThat(count("goal")).isEqualTo(6);
            assertThat(count("booking")).isEqualTo(4);
            assertThat(count("penalty_kick")).isEqualTo(2);
        }

        @Test
        void secondImportOfSameCompetitionIsIgnored() throws Exception {
            assertThat(importer.importFile(new ClassPathResource("worldcup-sample.json").getFile().toPath())).isFalse();
            assertThat(count("match")).isEqualTo(2);
        }

        @Test
        void convertsRoundKickoffAndScores() {
            List<MatchSummary> matches = ratingQueryService.findMatches();
            assertThat(matches).extracting(MatchSummary::stage).containsExactly("GROUP", "FINAL");

            MatchSummary group = matches.get(0);
            assertThat(group.groupName()).isEqualTo("A");
            assertThat(group.kickoffAt()).isEqualTo(OffsetDateTime.of(2026, 6, 11, 19, 0, 0, 0, ZoneOffset.UTC));
            assertThat(group.stadium()).isEqualTo("Alpha Stadium");
            assertThat(group.city()).isEqualTo("Alpha City");

            MatchSummary fin = matches.get(1);
            assertThat(fin.kickoffAt()).isEqualTo(OffsetDateTime.of(2026, 7, 19, 4, 0, 0, 0, ZoneOffset.UTC));
            assertThat(fin.extraTime()).isTrue();
            assertThat(List.of(fin.score1(), fin.score2(), fin.penalties1(), fin.penalties2())).containsExactly(1, 1, 1, 0);
        }

        @Test
        void ownGoalIsCreditedToOpponentWithOriginalScorer() {
            String row = jdbc.sql("""
                            SELECT t.name || '/' || p.name || '/' || pt.name
                            FROM goal g JOIN team t ON t.id = g.team_id
                            JOIN player p ON p.id = g.player_id JOIN team pt ON pt.id = p.team_id
                            WHERE g.is_own_goal
                            """)
                    .query(String.class).single();
            assertThat(row).isEqualTo("Beta/A2/Alpha");
        }

        @Test
        void shootoutKicksAreAssignedToTheShooterTeam() {
            List<String> kicks = jdbc.sql("""
                            SELECT k.kick_order || ':' || t.name || ':' || p.name || ':' || k.scored
                            FROM penalty_kick k JOIN team t ON t.id = k.team_id JOIN player p ON p.id = k.player_id
                            ORDER BY k.kick_order
                            """)
                    .query(String.class).list();
            assertThat(kicks).containsExactly("1:Alpha:A1:true", "2:Gamma:G1:false");
        }
    }

    @Nested
    class Ratings {

        @BeforeEach
        void computeRatings() throws Exception {
            runRatingScript();
        }

        @Test
        void ratesEveryPlayerWhoPlayedAndEveryTeam() {
            // match de groupe : 4 Alpha (dont 1 entrant) + 3 Beta ; finale : 3 Alpha + 3 Gamma (dont 1 entrant)
            assertThat(count("player_match_rating")).isEqualTo(13);
            assertThat(count("team_match_rating")).isEqualTo(4);
            assertThat(jdbc.sql("SELECT COUNT(*) FROM player_match_rating WHERE rating < 0 OR rating > 10")
                    .query(Integer.class).single()).isZero();
        }

        @Test
        void scriptCanBeRunAgainWithoutDuplicates() throws Exception {
            runRatingScript();
            assertThat(count("player_match_rating")).isEqualTo(13);
            assertThat(count("team_match_rating")).isEqualTo(4);
        }

        @Test
        void hatTrickWithWinningGoalIsCappedAtTen() {
            assertThat(playerRating("A1", "GROUP")).isEqualByComparingTo("10.0");
            assertThat(playerInt("decisive_goals", "A1", "GROUP")).isEqualTo(1);
            assertThat(playerInt("penalty_goals", "A1", "GROUP")).isEqualTo(1);
        }

        @Test
        void ownGoalAndYellowCardLowerTheRating() {
            // 6 + (victoire 0,5 + différentiel 0,6) + (csc -1 + jaune -0,5)
            assertThat(playerRating("A2", "GROUP")).isEqualByComparingTo("5.6");
            assertThat(playerInt("own_goals", "A2", "GROUP")).isEqualTo(1);
        }

        @Test
        void secondYellowEndsPlayingTimeAndIsPenalised() {
            // 6 + (défaite -0,5 + différentiel -0,3) + (jaune -0,5 + deuxième jaune -1)
            assertThat(playerRating("B2", "GROUP")).isEqualByComparingTo("3.7");
            assertThat(playerInt("minutes_played", "B2", "GROUP")).isEqualTo(80);
        }

        @Test
        void substitutionRecordedAfter120MinutesGivesZeroMinutes() {
            assertThat(playerInt("minutes_played", "G3", "FINAL")).isZero();
        }

        @Test
        void equalizerAndShootoutKicksAreRecorded() {
            assertThat(playerInt("decisive_goals", "A3", "FINAL")).isEqualTo(1);
            assertThat(playerInt("shootout_scored", "A1", "FINAL")).isEqualTo(1);
            assertThat(playerInt("shootout_missed", "G1", "FINAL")).isEqualTo(1);
        }

        @Test
        void teamRatingCombinesResultGoalsAndFairPlay() {
            // Beta : rouge direct -4, jaune -1, deuxième jaune -2
            assertThat(teamInt("fair_play_points", "Beta", "GROUP")).isEqualTo(-7);
            assertThat(teamRating("Beta", "GROUP")).isEqualByComparingTo("3.2");
            assertThat(teamRating("Alpha", "GROUP")).isEqualByComparingTo("8.8");
            assertThat(teamInt("points", "Alpha", "GROUP")).isEqualTo(3);
        }

        @Test
        void shootoutWinIsADrawWithBonusWeightedByFinalCoefficient() {
            assertThat(teamInt("points", "Alpha", "FINAL")).isEqualTo(1);
            // (tab +0,3 + buts +0,2) x 1,3 et (tab -0,3 + buts +0,2) x 1,3
            assertThat(teamRating("Alpha", "FINAL")).isEqualByComparingTo("6.7");
            assertThat(teamRating("Gamma", "FINAL")).isEqualByComparingTo("5.9");
        }

        @Test
        void queryServiceExposesRatings() {
            MatchSummary fin = ratingQueryService.findMatches().get(1);
            assertThat(fin.rating1()).isEqualByComparingTo("6.7");

            MatchRatings details = ratingQueryService.findMatchRatings(fin.id()).orElseThrow();
            assertThat(details.teams()).hasSize(2);
            assertThat(details.players()).hasSize(6);
            assertThat(details.players()).filteredOn(PlayerRating::captain).extracting(PlayerRating::name)
                    .containsExactlyInAnyOrder("A1", "G1");
            assertThat(ratingQueryService.findMatchRatings(-1)).isEmpty();

            assertThat(ratingQueryService.findPlayerRanking(2, 10)).extracting(p -> p.name())
                    .containsExactlyInAnyOrder("A1", "A2", "A3");
            assertThat(ratingQueryService.findTeamRanking()).extracting(TeamRanking::team)
                    .containsExactly("Alpha", "Gamma", "Beta");
        }

        @Test
        void tournamentStatsAggregateMatchesCardsAndRatings() {
            PlayerTournamentStats a1 = playerStats("A1");
            assertThat(a1.matchesPlayed()).isEqualTo(2);
            assertThat(a1.goals()).isEqualTo(3);
            assertThat(a1.foulsCommitted()).isNull();
            assertThat(a1.manOfTheMatch()).isNull();
            assertThat(a1.detailedStatsMatches()).isZero();

            // B2 : jaune puis deuxième jaune
            PlayerTournamentStats b2 = playerStats("B2");
            assertThat(List.of(b2.yellowCards(), b2.redCards())).containsExactly(1, 1);

            TeamTournamentStats beta = teamStats("Beta");
            assertThat(List.of(beta.matchesPlayed(), beta.yellowCards(), beta.redCards(), beta.fairPlayPoints()))
                    .containsExactly(1, 1, 2, -7);
            assertThat(beta.rating()).isEqualByComparingTo("3.2");
            assertThat(teamStats("Alpha").matchesPlayed()).isEqualTo(2);
        }

        @Test
        void tournamentStatsUseDetailedStatsWhenImported() {
            int finalId = ratingQueryService.findMatches().get(1).id();
            jdbc.sql("""
                            INSERT INTO player_match_stats (match_id, player_id, team_id, fouls_committed, distance_km,
                                                            is_man_of_the_match)
                            SELECT r.match_id, r.player_id, r.team_id, 2, 10.5, p.name = 'A1'
                            FROM player_match_rating r JOIN player p ON p.id = r.player_id
                            WHERE r.match_id = :id
                            """)
                    .param("id", finalId).update();
            jdbc.sql("""
                            INSERT INTO team_match_stats (match_id, team_id, corners)
                            SELECT r.match_id, r.team_id, 7 FROM team_match_rating r
                            JOIN team t ON t.id = r.team_id WHERE r.match_id = :id AND t.name = 'Alpha'
                            """)
                    .param("id", finalId).update();

            PlayerTournamentStats a1 = playerStats("A1");
            assertThat(a1.foulsCommitted()).isEqualTo(2);
            assertThat(a1.manOfTheMatch()).isTrue();
            assertThat(a1.manOfTheMatchCount()).isEqualTo(1);
            assertThat(a1.detailedStatsMatches()).isEqualTo(1);
            assertThat(playerStats("A2").manOfTheMatch()).isFalse();

            // total publié (corners) prioritaire, sinon somme des joueurs (fautes, distance)
            TeamTournamentStats alpha = teamStats("Alpha");
            assertThat(alpha.corners()).isEqualTo(7);
            assertThat(alpha.foulsCommitted()).isEqualTo(6);
            assertThat(alpha.distanceKm()).isEqualByComparingTo("31.5");
            assertThat(teamStats("Gamma").corners()).isNull();
            assertThat(teamStats("Beta").foulsCommitted()).isNull();
        }
    }

    private PlayerTournamentStats playerStats(String name) {
        return tournamentStatsQueryService.findPlayerStats(null).stream()
                .filter(p -> p.player().equals(name)).findFirst().orElseThrow();
    }

    private TeamTournamentStats teamStats(String team) {
        return tournamentStatsQueryService.findTeamStats(null).stream()
                .filter(t -> t.team().equals(team)).findFirst().orElseThrow();
    }

    private void runRatingScript() throws Exception {
        // Les méta-commandes psql (\set ...) ne passent pas par JDBC
        String sql = Files.readString(RATING_SCRIPT, StandardCharsets.UTF_8).lines()
                .filter(line -> !line.startsWith("\\"))
                .collect(Collectors.joining("\n"));
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private int count(String table) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table).query(Integer.class).single();
    }

    private BigDecimal playerRating(String player, String stage) {
        return jdbc.sql(playerSql("r.rating")).param("player", player).param("stage", stage)
                .query(BigDecimal.class).single();
    }

    private int playerInt(String column, String player, String stage) {
        return jdbc.sql(playerSql("r." + column)).param("player", player).param("stage", stage)
                .query(Integer.class).single();
    }

    private BigDecimal teamRating(String team, String stage) {
        return jdbc.sql(teamSql("r.rating")).param("team", team).param("stage", stage)
                .query(BigDecimal.class).single();
    }

    private int teamInt(String column, String team, String stage) {
        return jdbc.sql(teamSql("r." + column)).param("team", team).param("stage", stage)
                .query(Integer.class).single();
    }

    private static String playerSql(String column) {
        return "SELECT " + column + """
                 FROM player_match_rating r
                JOIN player p ON p.id = r.player_id JOIN match m ON m.id = r.match_id
                WHERE p.name = :player AND m.stage = :stage
                """;
    }

    private static String teamSql(String column) {
        return "SELECT " + column + """
                 FROM team_match_rating r
                JOIN team t ON t.id = r.team_id JOIN match m ON m.id = r.match_id
                WHERE t.name = :team AND m.stage = :stage
                """;
    }
}
