package stats_foot.stats_foot.player;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import stats_foot.stats_foot.database.DatabaseStatus;
import stats_foot.stats_foot.player.repository.JpaPlayerRepository;
import stats_foot.stats_foot.player.repository.PlayerRepository;

/** Runs the repository contract on the real JPA adapter, on the in-memory database imported at start-up. */
@SpringBootTest
@AutoConfigureMockMvc
class JpaPlayerRepositoryContractTest extends PlayerRepositoryContractTest {

	@Autowired
	private JpaPlayerRepository repository;

	@Autowired
	private DatabaseStatus databaseStatus;

	@Override
	protected PlayerRepository repository() {
		return repository;
	}

	@Test
	void databaseIsHealthyAfterStart() {
		assertThat(databaseStatus.isFailed()).isFalse();
	}

}
