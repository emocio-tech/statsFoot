package stats_foot.stats_foot.player.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import stats_foot.stats_foot.player.entity.PlayerEntity;

/** Spring Data access to the player table (database adapter only). */
public interface PlayerJpaRepository extends JpaRepository<PlayerEntity, String> {

}
