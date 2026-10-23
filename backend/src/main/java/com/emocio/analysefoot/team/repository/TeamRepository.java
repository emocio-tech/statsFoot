package com.emocio.analysefoot.team.repository;

import java.util.List;
import java.util.Optional;
import com.emocio.analysefoot.team.model.TeamWithStats;

/** Port giving access to the teams and their tournament totals. */
public interface TeamRepository {

	/** Returns all teams. @throws PlayerDataUnavailableException if the database cannot be read */
	List<TeamWithStats> findAll();

	/** Returns the team with the given id. @throws PlayerDataUnavailableException if the database cannot be read */
	Optional<TeamWithStats> findById(int id);

}
