package stats_foot.stats_foot.player;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Registers the JPA entities and repositories only with {@code statsfoot.player-source=database}.
 * The JPA/DataSource auto-configuration itself is re-enabled by the {@code database} profile.
 */
@Configuration
@ConditionalOnProperty(name = "statsfoot.player-source", havingValue = "database")
@EntityScan(basePackageClasses = PlayerEntity.class)
@EnableJpaRepositories(basePackageClasses = PlayerJpaRepository.class)
public class JpaConfig {

}
