package stats_foot.stats_foot.player.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import jakarta.persistence.PersistenceException;
import stats_foot.stats_foot.player.entity.PlayerEntity;
import stats_foot.stats_foot.player.exception.PlayerDataUnavailableException;
import stats_foot.stats_foot.player.model.Player;

/**
 * Reads the players from the database. Filtering, sorting and capping stay in {@code PlayerService}.
 * Any database access error becomes a
 * {@link PlayerDataUnavailableException} whose public message exposes no detail.
 */
@Repository
public class JpaPlayerRepository implements PlayerRepository {

	private final PlayerJpaRepository playerJpaRepository;

	public JpaPlayerRepository(PlayerJpaRepository playerJpaRepository) {
		this.playerJpaRepository = playerJpaRepository;
	}

	@Override
	public List<Player> findAll() {
		try {
			return playerJpaRepository.findAllWithTeamAndStats().stream().map(PlayerEntity::toPlayer).toList();
		} catch (DataAccessException | PersistenceException e) {
			throw unavailable(e);
		}
	}

	@Override
	public Optional<Player> findById(int id) {
		try {
			return playerJpaRepository.findByIdWithTeamAndStats(id).map(PlayerEntity::toPlayer);
		} catch (DataAccessException | PersistenceException e) {
			throw unavailable(e);
		}
	}

	private static PlayerDataUnavailableException unavailable(Exception cause) {
		return new PlayerDataUnavailableException(PlayerDataUnavailableException.UNAVAILABLE_MESSAGE, cause);
	}

}
