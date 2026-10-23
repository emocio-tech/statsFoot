package com.emocio.analysefoot.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.emocio.analysefoot.FullDataTest;
import com.emocio.analysefoot.team.dto.TeamComparison;
import com.emocio.analysefoot.team.exception.TeamNotFoundException;
import com.emocio.analysefoot.team.tool.TeamTools;

/** The team comparison tool of the internal LLM, on the real data. */
@FullDataTest
class TeamToolsTest {

	@Autowired
	private TeamTools teamTools;

	@Test
	void comparesTwoTeamsByNameOrCode() {
		TeamComparison byName = teamTools.compareTeams("France", "Spain");
		TeamComparison byCode = teamTools.compareTeams("fra", "ESP");

		assertThat(byName.teamA().name()).isEqualTo("France");
		assertThat(byName.teamB().name()).isEqualTo("Spain");
		assertThat(byCode).isEqualTo(byName);
		assertThat(byName.lines()).extracting("key").contains("rating", "fairPlayPoints", "finalPosition");
	}

	@Test
	void unknownTeamIsReported() {
		assertThatThrownBy(() -> teamTools.compareTeams("Atlantis", "France"))
				.isInstanceOf(TeamNotFoundException.class).hasMessageContaining("Atlantis");
	}

	@Test
	void ambiguousNameListsTheCandidates() {
		assertThatThrownBy(() -> teamTools.compareTeams("a", "France"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Several teams match");
	}

}
