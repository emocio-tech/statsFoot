package stats_foot.stats_foot.player.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import stats_foot.stats_foot.player.model.Team;

/** Database row of a national team (read only). */
@Entity
@Table(name = "team")
public class TeamEntity {

	@Id
	@Column(name = "id")
	private int id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "fifa_code", nullable = false)
	private String fifaCode;

	protected TeamEntity() {
	}

	public Team toTeam() {
		return new Team(id, name, fifaCode);
	}

}
