package stats_foot.stats_foot.player;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to the player table (database adapter only). */
public interface PlayerJpaRepository extends JpaRepository<PlayerEntity, String> {

}
