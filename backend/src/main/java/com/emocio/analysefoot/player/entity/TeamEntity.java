package com.emocio.analysefoot.player.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Formula;
import com.emocio.analysefoot.player.model.Team;

/** Database row of a national team (read only). */
@Entity
@Table(name = "team")
public class TeamEntity {

	@Id
	@Column(name = "id")
	private int id;

	@Column(name = "name", nullable = false)
	private String name;

	/** FIFA code from the reference table {@code team_fifa_code} (the {@code team} table has no such column). */
	@Formula("(select c.code from team_fifa_code c where c.team_name = name)")
	private String fifaCode;

	protected TeamEntity() {
	}

	public Team toTeam() {
		return new Team(id, name, fifaCode);
	}

}
