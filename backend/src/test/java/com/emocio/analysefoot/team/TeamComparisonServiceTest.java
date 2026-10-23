package com.emocio.analysefoot.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.emocio.analysefoot.player.dto.Leader;
import com.emocio.analysefoot.player.dto.PlayerComparison.ComparisonLine;
import com.emocio.analysefoot.player.model.Team;
import com.emocio.analysefoot.team.dto.TeamComparison;
import com.emocio.analysefoot.team.exception.SameTeamComparisonException;
import com.emocio.analysefoot.team.exception.TeamNotFoundException;
import com.emocio.analysefoot.team.model.TeamStats;
import com.emocio.analysefoot.team.model.TeamWithStats;
import com.emocio.analysefoot.team.repository.TeamRepository;
import com.emocio.analysefoot.team.service.TeamComparisonService;

class TeamComparisonServiceTest {

	private TeamRepository repository;
	private TeamComparisonService service;

	@BeforeEach
	void setUp() {
		repository = mock(TeamRepository.class);
		service = new TeamComparisonService(repository);
	}

	/** Statistics with every value set to 1 (Integer) or 1.0 (Double). */
	private static TeamStats stats() {
		return stats(1, 1, 1.0);
	}

	private static TeamStats stats(int goalsFor, int yellowCards, Double rating) {
		return new TeamStats(1, 1, 1, 1, 1, goalsFor, 1, yellowCards, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1.0, rating);
	}

	private static TeamWithStats team(int id, String name, TeamStats stats) {
		return new TeamWithStats(new Team(id, name, name.substring(0, 3).toUpperCase()), stats);
	}

	private void given(TeamWithStats... teams) {
		for (TeamWithStats t : teams) {
			when(repository.findById(t.team().id())).thenReturn(Optional.of(t));
		}
	}

	private static ComparisonLine line(TeamComparison report, String key) {
		return report.lines().stream().filter(l -> key.equals(l.key())).findFirst().orElseThrow();
	}

	@Test
	void compare_returnsIdentitiesAndOneLinePerStatistic() {
		given(team(1, "France", stats()), team(2, "Brazil", stats()));

		TeamComparison report = service.compare(1, 2);

		assertThat(report.teamA().name()).isEqualTo("France");
		assertThat(report.teamA().code()).isEqualTo("FRA");
		assertThat(report.teamB().id()).isEqualTo(2);
		assertThat(report.lines()).hasSize(20);
	}

	@Test
	void compare_higherIsBetter_forGoalsFor() {
		given(team(1, "France", stats(5, 1, 1.0)), team(2, "Brazil", stats(3, 1, 1.0)));

		ComparisonLine goals = line(service.compare(1, 2), "goalsFor");

		assertThat(goals.leader()).isEqualTo(Leader.A);
		assertThat(goals.difference()).isEqualTo(2);
	}

	@Test
	void compare_lowerIsBetter_forYellowCards() {
		given(team(1, "France", stats(1, 4, 1.0)), team(2, "Brazil", stats(1, 2, 1.0)));

		assertThat(line(service.compare(1, 2), "yellowCards").leader()).isEqualTo(Leader.B);
	}

	@Test
	void compare_equalValues_isATie() {
		given(team(1, "France", stats()), team(2, "Brazil", stats()));

		assertThat(line(service.compare(1, 2), "goalsFor").leader()).isEqualTo(Leader.TIE);
	}

	@Test
	void compare_contextStatistics_neverDesignateALeader() {
		TeamStats other = new TeamStats(9, 1, 5, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 99.0, 1.0);
		given(team(1, "France", stats()), team(2, "Brazil", other));

		TeamComparison report = service.compare(1, 2);

		for (String key : List.of("matchesPlayed", "draws", "distanceKm")) {
			assertThat(line(report, key).available()).isTrue();
			assertThat(line(report, key).difference()).isNotNull();
			assertThat(line(report, key).leader()).isNull();
		}
	}

	@Test
	void compare_missingValue_isUnavailableAndNotCounted() {
		given(team(1, "France", stats(1, 1, null)), team(2, "Brazil", stats()));

		TeamComparison report = service.compare(1, 2);

		ComparisonLine rating = line(report, "rating");
		assertThat(rating.available()).isFalse();
		assertThat(rating.difference()).isNull();
		assertThat(rating.leader()).isNull();
		assertThat(report.summary().leadsA() + report.summary().leadsB() + report.summary().ties()).isEqualTo(
				report.lines().stream().filter(l -> l.leader() != null).count());
	}

	@Test
	void compare_summaryAndVerdictNameTheLeader() {
		given(team(1, "France", stats(5, 1, 1.0)), team(2, "Brazil", stats(3, 1, 1.0)));

		TeamComparison report = service.compare(1, 2);

		assertThat(report.summary().leadsA()).isEqualTo(1);
		assertThat(report.summary().leadsB()).isZero();
		assertThat(report.verdict()).startsWith("France leads on 1 statistics to 0 for Brazil");
	}

	@Test
	void compare_sameId_isRejectedWithoutReadingTheRepository() {
		assertThatThrownBy(() -> service.compare(1, 1)).isInstanceOf(SameTeamComparisonException.class)
				.hasMessage("The two teams must be different.");
		verifyNoInteractions(repository);
	}

	@Test
	void compare_unknownTeam_namesTheUnknownOne() {
		given(team(2, "Brazil", stats()));
		given(team(1, "France", stats()));
		when(repository.findById(3)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.compare(3, 2)).isInstanceOf(TeamNotFoundException.class)
				.hasMessage("Team A not found.");
		assertThatThrownBy(() -> service.compare(1, 3)).isInstanceOf(TeamNotFoundException.class)
				.hasMessage("Team B not found.");
	}

}
