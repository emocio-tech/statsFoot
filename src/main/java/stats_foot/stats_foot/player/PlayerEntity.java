package stats_foot.stats_foot.player;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Database row of a player (database adapter only). Statistic columns are nullable so that {@code null}
 * stays distinct from {@code 0}; {@code has_goalkeeper_stats} tells whether the goalkeeper statistics exist.
 */
@Entity
@Table(name = "player")
public class PlayerEntity {

	@Id
	@Column(name = "id")
	private String id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "search_name", nullable = false)
	private String searchName;

	@ManyToOne(optional = false)
	@JoinColumn(name = "team_id", nullable = false)
	private TeamEntity team;

	@Column(name = "shirt_number")
	private Integer shirtNumber;

	@Column(name = "position")
	private String position;

	@Column(name = "position_detail")
	private String positionDetail;

	@Column(name = "birth_date")
	private LocalDate birthDate;

	@Column(name = "club")
	private String club;

	@Column(name = "height_cm")
	private Integer heightCm;

	@Column(name = "preferred_foot")
	private String preferredFoot;

	@Column(name = "captain")
	private Boolean captain;

	@Column(name = "appearances")
	private Integer appearances;

	@Column(name = "starts")
	private Integer starts;

	@Column(name = "minutes_played")
	private Integer minutesPlayed;

	@Column(name = "goals")
	private Integer goals;

	@Column(name = "assists")
	private Integer assists;

	@Column(name = "own_goals")
	private Integer ownGoals;

	@Column(name = "shots")
	private Integer shots;

	@Column(name = "shots_on_target")
	private Integer shotsOnTarget;

	@Column(name = "yellow_cards")
	private Integer yellowCards;

	@Column(name = "red_cards")
	private Integer redCards;

	@Column(name = "fouls_committed")
	private Integer foulsCommitted;

	@Column(name = "fouls_suffered")
	private Integer foulsSuffered;

	@Column(name = "passes")
	private Integer passes;

	@Column(name = "pass_accuracy")
	private Double passAccuracy;

	@Column(name = "has_goalkeeper_stats", nullable = false)
	private boolean hasGoalkeeperStats;

	@Column(name = "gk_saves")
	private Integer saves;

	@Column(name = "gk_goals_conceded")
	private Integer goalsConceded;

	@Column(name = "gk_clean_sheets")
	private Integer cleanSheets;

	@Column(name = "gk_penalties_saved")
	private Integer penaltiesSaved;

	protected PlayerEntity() {
	}

	public static PlayerEntity from(Player player, TeamEntity team) {
		PlayerEntity entity = new PlayerEntity();
		entity.id = player.id();
		entity.name = player.name();
		entity.searchName = NameNormalizer.normalize(player.name());
		entity.team = team;
		entity.shirtNumber = player.shirtNumber();
		entity.position = player.position();
		entity.positionDetail = player.positionDetail();
		entity.birthDate = player.birthDate();
		entity.club = player.club();
		entity.heightCm = player.heightCm();
		entity.preferredFoot = player.preferredFoot();
		entity.captain = player.captain();
		PlayerStats stats = player.stats();
		if (stats != null) {
			entity.appearances = stats.appearances();
			entity.starts = stats.starts();
			entity.minutesPlayed = stats.minutesPlayed();
			entity.goals = stats.goals();
			entity.assists = stats.assists();
			entity.ownGoals = stats.ownGoals();
			entity.shots = stats.shots();
			entity.shotsOnTarget = stats.shotsOnTarget();
			entity.yellowCards = stats.yellowCards();
			entity.redCards = stats.redCards();
			entity.foulsCommitted = stats.foulsCommitted();
			entity.foulsSuffered = stats.foulsSuffered();
			entity.passes = stats.passes();
			entity.passAccuracy = stats.passAccuracy();
		}
		GoalkeeperStats goalkeeperStats = player.goalkeeperStats();
		if (goalkeeperStats != null) {
			entity.hasGoalkeeperStats = true;
			entity.saves = goalkeeperStats.saves();
			entity.goalsConceded = goalkeeperStats.goalsConceded();
			entity.cleanSheets = goalkeeperStats.cleanSheets();
			entity.penaltiesSaved = goalkeeperStats.penaltiesSaved();
		}
		return entity;
	}

	public Player toPlayer() {
		PlayerStats stats = new PlayerStats(appearances, starts, minutesPlayed, goals, assists, ownGoals, shots,
				shotsOnTarget, yellowCards, redCards, foulsCommitted, foulsSuffered, passes, passAccuracy);
		GoalkeeperStats goalkeeperStats = hasGoalkeeperStats
				? new GoalkeeperStats(saves, goalsConceded, cleanSheets, penaltiesSaved)
				: null;
		return new Player(id, name, team.toTeam(), shirtNumber, position, positionDetail, birthDate, club, heightCm,
				preferredFoot, captain, stats, goalkeeperStats);
	}

	public String getId() {
		return id;
	}

	public String getSearchName() {
		return searchName;
	}

}
