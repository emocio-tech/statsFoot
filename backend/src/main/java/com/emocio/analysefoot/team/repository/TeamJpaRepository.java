package com.emocio.analysefoot.team.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.emocio.analysefoot.team.entity.TeamTournamentStatsEntity;

/** Spring Data access to the team totals view; the team is loaded in the same query. */
public interface TeamJpaRepository extends JpaRepository<TeamTournamentStatsEntity, Integer> {

	@Query("select s from TeamTournamentStatsEntity s join fetch s.team")
	List<TeamTournamentStatsEntity> findAllWithTeam();

	@Query("select s from TeamTournamentStatsEntity s join fetch s.team where s.teamId = :id")
	Optional<TeamTournamentStatsEntity> findByIdWithTeam(@Param("id") int id);

}
