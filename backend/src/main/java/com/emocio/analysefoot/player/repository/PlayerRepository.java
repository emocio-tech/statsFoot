package com.emocio.analysefoot.player.repository;

import java.util.List;
import java.util.Optional;
import com.emocio.analysefoot.player.model.Player;

/** Port giving access to the players. */
public interface PlayerRepository {

	/** Returns all players. @throws PlayerDataUnavailableException if the database cannot be read */
	List<Player> findAll();

	/** Returns the player with the given id. @throws PlayerDataUnavailableException if the database cannot be read */
	Optional<Player> findById(int id);

}
