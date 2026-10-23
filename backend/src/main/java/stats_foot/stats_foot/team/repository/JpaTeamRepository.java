package stats_foot.stats_foot.team.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import jakarta.persistence.PersistenceException;
import stats_foot.stats_foot.player.exception.PlayerDataUnavailableException;
import stats_foot.stats_foot.team.entity.TeamTournamentStatsEntity;
import stats_foot.stats_foot.team.model.TeamWithStats;

/**
 * Reads the teams from the database. Any database access error becomes a {@link PlayerDataUnavailableException} whose public message exposes no detail.
 */
@Repository
public class JpaTeamRepository implements TeamRepository {

	private final TeamJpaRepository teamJpaRepository;

	public JpaTeamRepository(TeamJpaRepository teamJpaRepository) {
		this.teamJpaRepository = teamJpaRepository;
	}

	@Override
	public List<TeamWithStats> findAll() {
		try {
			return teamJpaRepository.findAllWithTeam().stream().map(TeamTournamentStatsEntity::toTeamWithStats).toList();
		} catch (DataAccessException | PersistenceException e) {
			throw unavailable(e);
		}
	}

	@Override
	public Optional<TeamWithStats> findById(int id) {
		try {
			return teamJpaRepository.findByIdWithTeam(id).map(TeamTournamentStatsEntity::toTeamWithStats);
		} catch (DataAccessException | PersistenceException e) {
			throw unavailable(e);
		}
	}

	private static PlayerDataUnavailableException unavailable(Exception cause) {
		return new PlayerDataUnavailableException(PlayerDataUnavailableException.UNAVAILABLE_MESSAGE, cause);
	}

}
