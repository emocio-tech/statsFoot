package stats_foot.stats_foot.player;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Database row of a national team (database adapter only). */
@Entity
@Table(name = "team")
public class TeamEntity {

	@Id
	@Column(name = "id")
	private String id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "code", nullable = false)
	private String code;

	protected TeamEntity() {
	}

	public static TeamEntity from(Team team) {
		TeamEntity entity = new TeamEntity();
		entity.id = team.id();
		entity.name = team.name();
		entity.code = team.code();
		return entity;
	}

	public Team toTeam() {
		return new Team(id, name, code);
	}

}
