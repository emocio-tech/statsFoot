package stats_foot.stats_foot.player;

import java.util.List;
import java.util.Optional;

/** Port giving access to the players, whatever the underlying source. */
public interface PlayerRepository {

	/** Returns all players. @throws PlayerDataUnavailableException if the source cannot be read */
	List<Player> findAll();

	/** Returns the player with the given id. @throws PlayerDataUnavailableException if the source cannot be read */
	Optional<Player> findById(String id);

}
