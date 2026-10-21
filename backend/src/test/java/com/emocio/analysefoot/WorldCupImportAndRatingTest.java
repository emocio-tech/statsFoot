package com.emocio.analysefoot;

import com.emocio.analysefoot.analyse.AnalyseCriterion;
import com.emocio.analysefoot.analyse.AnalyseService;
import com.emocio.analysefoot.dto.AnalyseAnswer;
import com.emocio.analysefoot.dto.MatchRatings;
import com.emocio.analysefoot.dto.RankedEntry;
import com.emocio.analysefoot.dto.MatchSummary;
import com.emocio.analysefoot.dto.PlayerRating;
import com.emocio.analysefoot.dto.PlayerReport;
import com.emocio.analysefoot.dto.PlayerTournamentStats;
import com.emocio.analysefoot.dto.TeamRanking;
import com.emocio.analysefoot.dto.TeamReport;
import com.emocio.analysefoot.dto.TeamTournamentStats;
import com.emocio.analysefoot.importer.WorldCupImporter;
import com.emocio.analysefoot.service.RatingQueryService;
import com.emocio.analysefoot.service.ReportQueryService;
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

    private static final Path MIGRATIONS = Path.of("src", "main", "resources", "db", "migration");

    private static final Path DONNEES_STATS_SCRIPT = MIGRATIONS.resolve("donneesStats.sql");

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

    @Autowired
    private AnalyseService analyseService;

    @Autowired
    private ReportQueryService reportQueryService;

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

    /** Migration V4 et parties de données de donneesStats.sql (notes, stats fictives, passes décisives). */
    @Nested
    class DonneesStats {

        @BeforeEach
        void runDataParts() throws Exception {
            runDonneesStatsDataParts();
        }

        @Test
        void headerCitesEveryMigration() throws Exception {
            String header = String.join("\n", Files.readAllLines(DONNEES_STATS_SCRIPT, StandardCharsets.UTF_8).subList(0, 30));
            try (var files = Files.list(MIGRATIONS)) {
                List<String> migrations = files.map(p -> p.getFileName().toString())
                        .filter(name -> name.matches("V\\d+__.*\\.sql")).toList();
                assertThat(migrations).hasSizeGreaterThanOrEqualTo(4);
                assertThat(migrations).allSatisfy(name -> assertThat(header).contains(name));
            }
        }

        @Test
        void oneGoalkeeperPerTeamAndMatch() {
            assertThat(jdbc.sql("""
                            SELECT COUNT(*) FROM (SELECT match_id, team_id FROM match_lineup WHERE is_goalkeeper
                                                  GROUP BY match_id, team_id HAVING COUNT(*) = 1) x
                            """).query(Integer.class).single()).isEqualTo(4);
            assertThat(jdbc.sql("""
                            SELECT DISTINCT p.name FROM match_lineup l JOIN player p ON p.id = l.player_id
                            WHERE l.is_goalkeeper ORDER BY p.name
                            """).query(String.class).list()).containsExactly("A1", "B1", "G1");
        }

        @Test
        void assistsFollowTheCoherenceRules() {
            assertThat(count("goal_assist")).isPositive();
            assertThat(jdbc.sql("""
                            SELECT COUNT(*) FROM goal_assist a JOIN goal g ON g.id = a.goal_id
                            JOIN player p ON p.id = a.player_id
                            WHERE g.is_penalty OR g.is_own_goal OR p.team_id <> g.team_id OR a.player_id = g.player_id
                               -- entré en jeu après le but
                               OR EXISTS (SELECT 1 FROM substitution s WHERE s.match_id = g.match_id AND s.player_in_id = a.player_id
                                          AND s.minute * 100 + COALESCE(s.added_time, 0) >= g.minute * 100 + COALESCE(g.added_time, 0))
                               -- sorti ou exclu avant le but
                               OR EXISTS (SELECT 1 FROM substitution s WHERE s.match_id = g.match_id AND s.player_out_id = a.player_id
                                          AND s.minute * 100 + COALESCE(s.added_time, 0) < g.minute * 100 + COALESCE(g.added_time, 0))
                               OR EXISTS (SELECT 1 FROM booking b WHERE b.match_id = g.match_id AND b.player_id = a.player_id
                                          AND b.card IN ('R', 'Y/R')
                                          AND b.minute * 100 + COALESCE(b.added_time, 0) < g.minute * 100 + COALESCE(g.added_time, 0))
                               -- ni titulaire ni remplaçant entré
                               OR NOT EXISTS (SELECT 1 FROM player_match_rating r WHERE r.match_id = g.match_id AND r.player_id = a.player_id)
                            """).query(Integer.class).single()).isZero();
        }

        @Test
        void generationIsDeterministic() throws Exception {
            String sql = "SELECT goal_id || ':' || player_id FROM goal_assist ORDER BY goal_id";
            List<String> first = jdbc.sql(sql).query(String.class).list();
            runDonneesStatsDataParts();
            assertThat(jdbc.sql(sql).query(String.class).list()).isEqualTo(first);
        }

        @Test
        void playerViewExposesCardsAndFairPlay() {
            assertThat(playerView("second_yellows", "B2")).isEqualTo(1);
            assertThat(playerView("fair_play_points", "B2")).isEqualTo(-3);
            assertThat(playerView("direct_reds", "B3")).isEqualTo(1);
            assertThat(playerView("fair_play_points", "B3")).isEqualTo(-4);
        }

        @Test
        void teamViewGivesFinalPosition() {
            assertThat(jdbc.sql("SELECT team || ':' || final_position FROM v_team_tournament_stats ORDER BY final_position")
                    .query(String.class).list()).containsExactly("Alpha:1", "Gamma:2", "Beta:8");
        }

        private int playerView(String column, String player) {
            return jdbc.sql("SELECT " + column + " FROM v_player_tournament_stats WHERE player = :player")
                    .param("player", player).query(Integer.class).single();
        }
    }

    /**
     * Critères de l'onglet Analyse : pour chacun, le vainqueur et la règle de départage.
     * Temps de jeu du jeu d'essai : A1 210, A2 210, A3 180, A4 30, B1 90, B2 80, B3 20, G1 120, G2 120, G3 0.
     */
    @Nested
    class Analyse {

        @BeforeEach
        void runDataParts() throws Exception {
            runDonneesStatsDataParts();
        }

        @Test
        void topScorer() {
            AnalyseAnswer answer = answer(AnalyseCriterion.TOP_SCORER);
            assertThat(names(answer.winners())).containsExactly("A1");
            assertThat(answer.winners().getFirst().value()).isEqualByComparingTo("3");
            assertThat(answer.fictitious()).isFalse();
        }

        @Test
        void topScorerTieGoesToFewerMinutes() {
            setPlayerTotal("goals", "B2", 3);
            assertThat(ranks(answer(AnalyseCriterion.TOP_SCORER))).startsWith("1:B2", "2:A1");
        }

        @Test
        void topAssistIsFictitious() {
            AnalyseAnswer answer = answer(AnalyseCriterion.TOP_ASSIST);
            assertThat(answer.fictitious()).isTrue();
            int max = jdbc.sql("SELECT MAX(n) FROM (SELECT COUNT(*) n FROM goal_assist GROUP BY player_id) x")
                    .query(Integer.class).single();
            assertThat(answer.winners()).isNotEmpty().allSatisfy(w -> assertThat(w.value()).isEqualByComparingTo(String.valueOf(max)));
        }

        @Test
        void topAssistTieGoesToFewerMinutes() {
            jdbc.sql("DELETE FROM goal_assist").update();
            insertAssist("A2", 10, null);
            insertAssist("A4", 90, 2);
            assertThat(ranks(answer(AnalyseCriterion.TOP_ASSIST))).containsExactly("1:A4", "2:A2");
        }

        @Test
        void topAssistWithoutAssistsHasNoData() {
            jdbc.sql("DELETE FROM goal_assist").update();
            assertThat(answer(AnalyseCriterion.TOP_ASSIST).status()).isEqualTo("NO_DATA");
        }

        @Test
        void mostYellowCardsTieGoesToMostPerNinetyMinutes() {
            // A2 et B2 : 1 jaune simple chacun ; B2 en 80 min, A2 en 210 min
            assertThat(ranks(answer(AnalyseCriterion.MOST_YELLOW_CARDS))).containsExactly("1:B2", "2:A2");
        }

        @Test
        void leastFairPlayPlayer() {
            assertThat(ranks(answer(AnalyseCriterion.LEAST_FAIR_PLAY_PLAYER))).containsExactly("1:B3", "2:B2", "3:A2");
        }

        @Test
        void leastFairPlayPlayerTieGoesToFewerMinutes() {
            // A3 (180 min) prend un rouge direct : -4 comme B3 (20 min)
            jdbc.sql("""
                            INSERT INTO booking (match_id, team_id, player_id, card, minute)
                            SELECT m.id, p.team_id, p.id, 'R', 85 FROM match m, player p
                            WHERE m.stage = 'GROUP' AND p.name = 'A3'
                            """).update();
            assertThat(ranks(answer(AnalyseCriterion.LEAST_FAIR_PLAY_PLAYER))).startsWith("1:B3", "2:A3");
        }

        @Test
        void bestTeamIsTheChampion() {
            AnalyseAnswer answer = answer(AnalyseCriterion.BEST_TEAM);
            // Alpha gagne la finale aux tirs au but
            assertThat(ranks(answer)).containsExactly("1:Alpha", "2:Gamma", "3:Beta");
            assertThat(answer.winners().getFirst().valueLabel()).isEqualTo("champion");
        }

        @Test
        void bestTeamTieGoesToBestRating() {
            // Delta, éliminée en groupes comme Beta, moins bien notée (Beta : (3,2 + 9,0) / 2)
            jdbc.sql("INSERT INTO team (name) VALUES ('Delta')").update();
            jdbc.sql("""
                            INSERT INTO match (competition_id, stage, group_name, kickoff_at, stadium_id, team1_id, team2_id,
                                               ft_team1, ft_team2)
                            SELECT c.id, 'GROUP', 'A', TIMESTAMPTZ '2026-06-01 18:00Z', s.id, b.id, d.id, 1, 0
                            FROM competition c, stadium s, team b, team d
                            WHERE b.name = 'Beta' AND d.name = 'Delta' LIMIT 1
                            """).update();
            jdbc.sql("""
                            INSERT INTO team_match_rating (match_id, team_id, rating, result, points, goals_for, goals_against,
                                                           fair_play_points, algorithm_version)
                            SELECT m.id, t.id, CASE t.name WHEN 'Beta' THEN 9.0 ELSE 5.0 END,
                                   CASE t.name WHEN 'Beta' THEN 'W' ELSE 'L' END, CASE t.name WHEN 'Beta' THEN 3 ELSE 0 END,
                                   CASE t.name WHEN 'Beta' THEN 1 ELSE 0 END, CASE t.name WHEN 'Beta' THEN 0 ELSE 1 END, 0, 'test'
                            FROM match m JOIN team t ON t.id IN (m.team1_id, m.team2_id)
                            WHERE m.kickoff_at = TIMESTAMPTZ '2026-06-01 18:00Z'
                            """).update();
            assertThat(ranks(answer(AnalyseCriterion.BEST_TEAM))).containsExactly("1:Alpha", "2:Gamma", "3:Beta", "4:Delta");
        }

        @Test
        void topScoringTeamTieGoesToGoalDifference() {
            // Beta et Gamma : 1 but chacune ; Gamma à 0 de différence, Beta à -2
            assertThat(ranks(answer(AnalyseCriterion.TOP_SCORING_TEAM))).containsExactly("1:Alpha", "2:Gamma", "3:Beta");
        }

        @Test
        void leastFairPlayTeam() {
            AnalyseAnswer answer = answer(AnalyseCriterion.LEAST_FAIR_PLAY_TEAM);
            assertThat(names(answer.winners())).containsExactly("Beta");
            assertThat(answer.winners().getFirst().value()).isEqualByComparingTo("-7");
        }

        @Test
        void leastFairPlayTeamTieKeepsBothTeams() {
            jdbc.sql("""
                            UPDATE team_match_rating SET fair_play_points = -7
                            WHERE team_id = (SELECT id FROM team WHERE name = 'Gamma')
                            """).update();
            assertThat(names(answer(AnalyseCriterion.LEAST_FAIR_PLAY_TEAM).winners())).containsExactlyInAnyOrder("Beta", "Gamma");
        }

        @Test
        void mostFairPlayPlayerExcludesGoalkeepers() {
            // A1 (gardien, 210 min sans carton) est exclu ; A3 : 180 min sans carton
            AnalyseAnswer answer = answer(AnalyseCriterion.MOST_FAIR_PLAY_PLAYER);
            assertThat(names(answer.winners())).containsExactly("A3");
            assertThat(names(answer.top())).doesNotContain("A1", "B1", "G1", "A2", "B2", "B3");
        }

        @Test
        void mostFairPlayPlayerTieKeepsBothPlayers() {
            setPlayerTotal("minutes_played", "G2", 180);
            assertThat(names(answer(AnalyseCriterion.MOST_FAIR_PLAY_PLAYER).winners())).containsExactlyInAnyOrder("A3", "G2");
        }

        @Test
        void everyCriterionRunsOnPostgres() {
            assertThat(AnalyseCriterion.values()).allSatisfy(c ->
                    assertThat(answer(c).status()).as(c.name()).isIn("ANSWERED", "NO_DATA"));
        }

        @Test
        void onlyTopAssistIsFictitious() {
            assertThat(AnalyseCriterion.values()).filteredOn(AnalyseCriterion::fictitious)
                    .containsExactly(AnalyseCriterion.TOP_ASSIST);
        }

        @Test
        void noRatingsMeansNoData() {
            jdbc.sql("DELETE FROM player_match_rating").update();
            assertThat(answer(AnalyseCriterion.TOP_SCORER).status()).isEqualTo("NO_DATA");
        }

        @Test
        void playerReportOfA1() {
            int a1 = playerId("A1");
            PlayerReport report = reportQueryService.playerReport(a1).orElseThrow();
            assertThat(report.goalkeeper()).isTrue();
            assertThat(report.totals().goals()).isEqualTo(3);
            assertThat(report.matches()).extracting(PlayerReport.MatchLine::stage).containsExactly("GROUP", "FINAL");
            assertThat(report.matches().getFirst().score()).isEqualTo("3-1");
            assertThat(report.bestMatch().stage()).isEqualTo("GROUP");
            assertThat(report.ranks()).filteredOn(r -> r.questionId().equals("TOP_SCORER"))
                    .singleElement().satisfies(r -> assertThat(r.rank()).isEqualTo(1));
            // gardien : hors de la population du joueur le plus fair-play
            assertThat(report.ranks()).filteredOn(r -> r.questionId().equals("MOST_FAIR_PLAY_PLAYER"))
                    .singleElement().satisfies(r -> assertThat(r.rank()).isNull());
            assertThat(report.fictitiousFields()).contains("assists", "foulsCommitted");
            assertThat(reportQueryService.playerReport(-1)).isEmpty();
        }

        @Test
        void teamReportOfAlpha() {
            int alpha = jdbc.sql("SELECT id FROM team WHERE name = 'Alpha'").query(Integer.class).single();
            TeamReport report = reportQueryService.teamReport(alpha).orElseThrow();
            assertThat(report.totals().finalPosition()).isEqualTo(1);
            assertThat(report.matches()).extracting(TeamReport.MatchLine::opponent).containsExactly("Beta", "Gamma");
            assertThat(report.matches().get(1).penalties()).isEqualTo("1-0");
            assertThat(report.squad()).extracting(TeamReport.SquadLine::name).contains("A1", "A2", "A3", "A4");
            assertThat(report.topScorer().name()).isEqualTo("A1");
            assertThat(reportQueryService.teamReport(-1)).isEmpty();
        }

        @Test
        void missingDetailedStatsAreNull() {
            jdbc.sql("DELETE FROM player_match_stats").update();
            jdbc.sql("DELETE FROM team_match_stats").update();
            PlayerReport report = reportQueryService.playerReport(playerId("A2")).orElseThrow();
            assertThat(report.totals().foulsCommitted()).isNull();
            assertThat(report.totals().distanceKm()).isNull();
        }

        private int playerId(String name) {
            return jdbc.sql("SELECT id FROM player WHERE name = :n").param("n", name).query(Integer.class).single();
        }

        private AnalyseAnswer answer(AnalyseCriterion criterion) {
            return analyseService.answer(criterion);
        }

        private static List<String> names(List<RankedEntry> entries) {
            return entries.stream().map(RankedEntry::name).toList();
        }

        private static List<String> ranks(AnalyseAnswer answer) {
            return answer.top().stream().map(e -> e.rank() + ":" + e.name()).toList();
        }

        /** Fixe le total d'un joueur sur le tournoi en reportant la valeur sur un seul de ses matchs. */
        private void setPlayerTotal(String column, String player, int total) {
            jdbc.sql("UPDATE player_match_rating SET " + column + " = 0 WHERE player_id = (SELECT id FROM player WHERE name = :p)")
                    .param("p", player).update();
            jdbc.sql("""
                            UPDATE player_match_rating SET %s = :total
                            WHERE (match_id, player_id) = (SELECT MIN(match_id), MIN(player_id) FROM player_match_rating
                                                           WHERE player_id = (SELECT id FROM player WHERE name = :p))
                            """.formatted(column))
                    .param("p", player).param("total", total).update();
        }

        private void insertAssist(String passer, int minute, Integer addedTime) {
            jdbc.sql("""
                            INSERT INTO goal_assist (goal_id, player_id)
                            SELECT g.id, p.id FROM goal g, player p
                            WHERE g.minute = :minute AND g.added_time IS NOT DISTINCT FROM :added AND p.name = :p
                            """).param("minute", minute).param("added", addedTime).param("p", passer).update();
        }
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

    /**
     * Exécute seulement les parties de données de donneesStats.sql (3 : notes, 6 : stats fictives) :
     * les parties de schéma ne sont jamais rejouées sur une base gérée par Flyway.
     */
    private void runDonneesStatsDataParts() throws Exception {
        List<String> lines = Files.readAllLines(DONNEES_STATS_SCRIPT, StandardCharsets.UTF_8);
        StringBuilder sql = new StringBuilder();
        boolean keep = false;
        for (String line : lines) {
            if (line.startsWith("-- PARTIE 3 ") || line.startsWith("-- PARTIE 6 ")) {
                keep = true;
            } else if (line.startsWith("-- PARTIE 4 ")) {
                keep = false;
            }
            if (keep && !line.startsWith("\\")) {
                sql.append(line).append('\n');
            }
        }
        try (Connection connection = dataSource.getConnection()) {
            try {
                ScriptUtils.executeSqlScript(connection, new ByteArrayResource(sql.toString().getBytes(StandardCharsets.UTF_8)));
            } catch (RuntimeException e) {
                // le script ouvre sa propre transaction : ne pas rendre au pool une connexion en échec
                try (var statement = connection.createStatement()) {
                    statement.execute("ROLLBACK");
                }
                throw e;
            }
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
