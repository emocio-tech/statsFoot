package stats_foot.stats_foot.player.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import stats_foot.stats_foot.player.entity.PlayerEntity;
import stats_foot.stats_foot.player.exception.PlayerDataUnavailableException;
import stats_foot.stats_foot.player.model.Player;

/**
 * Database adapter of {@link PlayerRepository}, active only with {@code statsfoot.player-source=database}.
 * Filtering, sorting and capping stay in {@link PlayerService}, so results match the JSON adapter.
 */
@Repository
@ConditionalOnProperty(name = "statsfoot.player-source", havingValue = "database")
public class JpaPlayerRepository implements PlayerRepository {

	private final PlayerJpaRepository playerJpaRepository;

	public JpaPlayerRepository(PlayerJpaRepository playerJpaRepository) {
		this.playerJpaRepository = playerJpaRepository;
	}

	@Override
	public List<Player> findAll() {
		try {
			return playerJpaRepository.findAll().stream().map(PlayerEntity::toPlayer).toList();
		} catch (DataAccessException e) {
			throw new PlayerDataUnavailableException(PlayerDataUnavailableException.UNAVAILABLE_MESSAGE, e);
		}
	}

	@Override
	public Optional<Player> findById(String id) {
		if (id == null) {
			return Optional.empty();
		}
		try {
			return playerJpaRepository.findById(id).map(PlayerEntity::toPlayer);
		} catch (DataAccessException e) {
			throw new PlayerDataUnavailableException(PlayerDataUnavailableException.UNAVAILABLE_MESSAGE, e);
		}
	}

}
