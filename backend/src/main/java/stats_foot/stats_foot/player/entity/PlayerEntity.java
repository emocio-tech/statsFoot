package stats_foot.stats_foot.player.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import stats_foot.stats_foot.player.model.Player;
import stats_foot.stats_foot.player.model.PlayerStats;

/**
 * Database row of a player (read only), with its team and its tournament totals. The totals come from the
 * view {@code v_player_tournament_stats} through a read-only one-to-one on the player id; a player who
 * never entered the pitch has no row in the view, so the association may be {@code null}.
 */
@Entity
@Table(name = "player")
public class PlayerEntity {

	@Id
	@Column(name = "id")
	private int id;

	@Column(name = "name", nullable = false)
	private String name;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "team_id", nullable = false)
	private TeamEntity team;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id", referencedColumnName = "player_id", insertable = false, updatable = false)
	private PlayerTournamentStatsEntity tournamentStats;

	protected PlayerEntity() {
	}

	public Player toPlayer() {
		if (tournamentStats == null) {
			return new Player(id, name, team.toTeam(), 0, 0, new PlayerStats(null, null, null, null, null, null, null,
					null, null, null, null, null, null, null, null, null, null, null, null));
		}
		return new Player(id, name, team.toTeam(), tournamentStats.goalkeeperMatchesOrZero(),
				tournamentStats.captainMatchesOrZero(), tournamentStats.toStats());
	}

}
