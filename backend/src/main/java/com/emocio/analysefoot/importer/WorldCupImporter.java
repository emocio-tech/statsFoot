package com.emocio.analysefoot.importer;

import com.emocio.analysefoot.importer.json.WorldCupJson;
import com.emocio.analysefoot.importer.json.WorldCupJson.BookingJson;
import com.emocio.analysefoot.importer.json.WorldCupJson.GoalJson;
import com.emocio.analysefoot.importer.json.WorldCupJson.LineupJson;
import com.emocio.analysefoot.importer.json.WorldCupJson.LineupPlayerJson;
import com.emocio.analysefoot.importer.json.WorldCupJson.MatchJson;
import com.emocio.analysefoot.importer.json.WorldCupJson.PenaltyJson;
import com.emocio.analysefoot.importer.json.WorldCupJson.RefereeJson;
import com.emocio.analysefoot.importer.json.WorldCupJson.ScoreJson;
import com.emocio.analysefoot.importer.json.WorldCupJson.SubJson;
import com.emocio.analysefoot.model.Booking;
import com.emocio.analysefoot.model.Card;
import com.emocio.analysefoot.model.Competition;
import com.emocio.analysefoot.model.Goal;
import com.emocio.analysefoot.model.Match;
import com.emocio.analysefoot.model.MatchLineup;
import com.emocio.analysefoot.model.PenaltyKick;
import com.emocio.analysefoot.model.Player;
import com.emocio.analysefoot.model.Referee;
import com.emocio.analysefoot.model.Stadium;
import com.emocio.analysefoot.model.Substitution;
import com.emocio.analysefoot.model.Team;
import com.emocio.analysefoot.repository.BookingRepository;
import com.emocio.analysefoot.repository.CompetitionRepository;
import com.emocio.analysefoot.repository.GoalRepository;
import com.emocio.analysefoot.repository.MatchLineupRepository;
import com.emocio.analysefoot.repository.MatchRepository;
import com.emocio.analysefoot.repository.PenaltyKickRepository;
import com.emocio.analysefoot.repository.PlayerRepository;
import com.emocio.analysefoot.repository.RefereeRepository;
import com.emocio.analysefoot.repository.StadiumRepository;
import com.emocio.analysefoot.repository.SubstitutionRepository;
import com.emocio.analysefoot.repository.TeamRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Importe une compétition au format openfootball (worldcup-full.json).
 * L'import est ignoré si une compétition du même nom existe déjà.
 */
@Service
public class WorldCupImporter {

    private static final Logger log = LoggerFactory.getLogger(WorldCupImporter.class);

    private final ObjectMapper objectMapper;
    private final CompetitionRepository competitionRepository;
    private final TeamRepository teamRepository;
    private final StadiumRepository stadiumRepository;
    private final RefereeRepository refereeRepository;
    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;
    private final MatchLineupRepository matchLineupRepository;
    private final SubstitutionRepository substitutionRepository;
    private final GoalRepository goalRepository;
    private final BookingRepository bookingRepository;
    private final PenaltyKickRepository penaltyKickRepository;

    public WorldCupImporter(ObjectMapper objectMapper,
                            CompetitionRepository competitionRepository,
                            TeamRepository teamRepository,
                            StadiumRepository stadiumRepository,
                            RefereeRepository refereeRepository,
                            PlayerRepository playerRepository,
                            MatchRepository matchRepository,
                            MatchLineupRepository matchLineupRepository,
                            SubstitutionRepository substitutionRepository,
                            GoalRepository goalRepository,
                            BookingRepository bookingRepository,
                            PenaltyKickRepository penaltyKickRepository) {
        this.objectMapper = objectMapper;
        this.competitionRepository = competitionRepository;
        this.teamRepository = teamRepository;
        this.stadiumRepository = stadiumRepository;
        this.refereeRepository = refereeRepository;
        this.playerRepository = playerRepository;
        this.matchRepository = matchRepository;
        this.matchLineupRepository = matchLineupRepository;
        this.substitutionRepository = substitutionRepository;
        this.goalRepository = goalRepository;
        this.bookingRepository = bookingRepository;
        this.penaltyKickRepository = penaltyKickRepository;
    }

    /** @return true si la compétition a été importée, false si elle existait déjà */
    @Transactional
    public boolean importFile(Path file) throws IOException {
        WorldCupJson data = read(file);
        if (competitionRepository.existsByName(data.name())) {
            log.info("Compétition '{}' déjà présente, import ignoré", data.name());
            return false;
        }

        Competition competition = competitionRepository.save(new Competition(data.name()));
        Lookups lookups = new Lookups();
        for (MatchJson match : data.matches()) {
            importMatch(competition, match, lookups);
        }
        log.info("Compétition '{}' importée : {} matchs, {} équipes, {} joueurs",
                data.name(), data.matches().size(), lookups.teams.size(), lookups.players.size());
        return true;
    }

    private WorldCupJson read(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        // Le fichier peut commencer par une ligne parasite avant le JSON
        int start = content.indexOf('{');
        if (start < 0) {
            throw new IllegalArgumentException("Aucun objet JSON dans " + file);
        }
        return objectMapper.readValue(content.substring(start), WorldCupJson.class);
    }

    private void importMatch(Competition competition, MatchJson json, Lookups lookups) {
        Team team1 = lookups.team(json.team1());
        Team team2 = lookups.team(json.team2());
        WorldCupParsers.Round round = WorldCupParsers.parseRound(json.round());
        Referee referee = json.referees().isEmpty() ? null : lookups.referee(json.referees().getFirst());

        Match match = new Match(competition, round.stage(), round.group(),
                WorldCupParsers.parseKickoff(json.date(), json.time()),
                lookups.stadium(json.ground()), json.attendance(), referee, team1, team2);
        applyScore(match, json.score());
        matchRepository.save(match);

        if (json.lineup().size() != 2) {
            throw new IllegalStateException("Composition incomplète pour " + json.team1() + " - " + json.team2());
        }
        Map<String, Player> roster1 = importLineup(match, team1, json.lineup().get(0), lookups);
        Map<String, Player> roster2 = importLineup(match, team2, json.lineup().get(1), lookups);

        importGoals(match, team1, team2, roster1, roster2, json.goals1(), lookups);
        importGoals(match, team2, team1, roster2, roster1, json.goals2(), lookups);

        if (!json.bookings().isEmpty()) {
            importBookings(match, team1, roster1, json.bookings().get(0), lookups);
            importBookings(match, team2, roster2, json.bookings().get(1), lookups);
        }

        importPenalties(match, team1, team2, roster1, roster2, json.penalties());
    }

    private static void applyScore(Match match, ScoreJson score) {
        if (score == null) {
            return;
        }
        match.setHalfTimeScore(side(score.ht(), 0), side(score.ht(), 1));
        match.setFullTimeScore(side(score.ft(), 0), side(score.ft(), 1));
        match.setExtraTimeScore(side(score.et(), 0), side(score.et(), 1));
        match.setPenaltyScore(side(score.p(), 0), side(score.p(), 1));
    }

    private static Short side(List<Integer> score, int index) {
        return score == null ? null : score.get(index).shortValue();
    }

    /** @return les joueurs de la feuille de match, par nom */
    private Map<String, Player> importLineup(Match match, Team team, LineupJson lineup, Lookups lookups) {
        Map<String, Player> roster = new HashMap<>();
        for (LineupPlayerJson starter : lineup.starter()) {
            Player player = lookups.player(team, starter.name());
            roster.put(starter.name(), player);
            matchLineupRepository.save(new MatchLineup(match, player, team, true, starter.captain()));
        }
        for (LineupPlayerJson substitute : lineup.bench()) {
            Player player = lookups.player(team, substitute.name());
            roster.put(substitute.name(), player);
            matchLineupRepository.save(new MatchLineup(match, player, team, false, substitute.captain()));
        }
        for (SubJson sub : lineup.subs()) {
            WorldCupParsers.Minute minute = WorldCupParsers.parseMinute(sub.minute());
            substitutionRepository.save(new Substitution(match, team,
                    rosterPlayer(team, roster, sub.on(), lookups),
                    rosterPlayer(team, roster, sub.off(), lookups),
                    minute.minute(), minute.addedTime()));
        }
        return roster;
    }

    /** Buts de {@code team} ; un but contre son camp est marqué par un joueur de {@code opponent}. */
    private void importGoals(Match match, Team team, Team opponent,
                             Map<String, Player> roster, Map<String, Player> opponentRoster,
                             List<GoalJson> goals, Lookups lookups) {
        for (GoalJson goal : goals) {
            Player scorer = goal.owngoal()
                    ? rosterPlayer(opponent, opponentRoster, goal.name(), lookups)
                    : rosterPlayer(team, roster, goal.name(), lookups);
            WorldCupParsers.Minute minute = WorldCupParsers.parseMinute(goal.minute());
            goalRepository.save(new Goal(match, team, scorer, minute.minute(), minute.addedTime(),
                    goal.penalty(), goal.owngoal()));
        }
    }

    private void importBookings(Match match, Team team, Map<String, Player> roster,
                                List<BookingJson> bookings, Lookups lookups) {
        for (BookingJson booking : bookings) {
            WorldCupParsers.Minute minute = WorldCupParsers.parseMinute(booking.minute());
            bookingRepository.save(new Booking(match, team, rosterPlayer(team, roster, booking.name(), lookups),
                    Card.fromCode(booking.type()), minute.minute(), minute.addedTime()));
        }
    }

    /** Le JSON ne donne pas l'équipe du tireur : on la retrouve via la feuille de match. */
    private void importPenalties(Match match, Team team1, Team team2,
                                 Map<String, Player> roster1, Map<String, Player> roster2,
                                 List<PenaltyJson> penalties) {
        short order = 1;
        for (PenaltyJson penalty : penalties) {
            Player player = roster1.get(penalty.name());
            Team team = team1;
            if (player == null) {
                player = roster2.get(penalty.name());
                team = team2;
            }
            if (player == null) {
                throw new IllegalStateException("Tireur absent des compositions : " + penalty.name());
            }
            penaltyKickRepository.save(new PenaltyKick(match, order++, team, player,
                    !"missed".equals(penalty.note())));
        }
    }

    /** Joueur de la feuille de match ; créé s'il n'y figure pas (donnée incomplète). */
    private Player rosterPlayer(Team team, Map<String, Player> roster, String name, Lookups lookups) {
        Player player = roster.get(name);
        if (player == null) {
            log.warn("{} absent de la feuille de match de {}", name, team.getName());
            player = lookups.player(team, name);
            roster.put(name, player);
        }
        return player;
    }

    /** Caches des référentiels pour la durée d'un import. */
    private final class Lookups {

        private final Map<String, Team> teams = new HashMap<>();
        private final Map<String, Stadium> stadiums = new HashMap<>();
        private final Map<String, Referee> referees = new HashMap<>();
        private final Map<String, Player> players = new HashMap<>();

        Team team(String name) {
            return teams.computeIfAbsent(name, key -> teamRepository.findByName(key)
                    .orElseGet(() -> teamRepository.save(new Team(key))));
        }

        Stadium stadium(String ground) {
            return stadiums.computeIfAbsent(ground, key -> {
                WorldCupParsers.Ground parsed = WorldCupParsers.parseGround(key);
                return stadiumRepository.findByNameAndCity(parsed.name(), parsed.city())
                        .orElseGet(() -> stadiumRepository.save(new Stadium(parsed.name(), parsed.city())));
            });
        }

        Referee referee(RefereeJson json) {
            return referees.computeIfAbsent(json.name(), key -> refereeRepository.findByName(key)
                    .orElseGet(() -> refereeRepository.save(new Referee(key, json.country()))));
        }

        Player player(Team team, String name) {
            return players.computeIfAbsent(team.getName() + "|" + name, key -> playerRepository.findByNameAndTeam(name, team)
                    .orElseGet(() -> playerRepository.save(new Player(name, team))));
        }
    }
}
