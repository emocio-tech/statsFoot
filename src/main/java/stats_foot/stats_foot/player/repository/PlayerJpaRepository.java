package stats_foot.stats_foot.player.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import stats_foot.stats_foot.player.entity.PlayerEntity;

/** Spring Data access to the player table; the team and the totals are loaded in the same query. */
public interface PlayerJpaRepository extends JpaRepository<PlayerEntity, Integer> {

	@Query("select p from PlayerEntity p join fetch p.team left join fetch p.tournamentStats")
	List<PlayerEntity> findAllWithTeamAndStats();

	@Query("select p from PlayerEntity p join fetch p.team left join fetch p.tournamentStats where p.id = :id")
	Optional<PlayerEntity> findByIdWithTeamAndStats(@Param("id") int id);

}
