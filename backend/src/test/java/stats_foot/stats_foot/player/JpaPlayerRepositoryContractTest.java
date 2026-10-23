package stats_foot.stats_foot.player;

import org.springframework.beans.factory.annotation.Autowired;
import stats_foot.stats_foot.FullDataTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import stats_foot.stats_foot.player.repository.JpaPlayerRepository;
import stats_foot.stats_foot.player.repository.PlayerRepository;

/** Runs the repository contract on the real JPA adapter, on the embedded PostgreSQL filled at start-up. */
@FullDataTest
@AutoConfigureMockMvc
class JpaPlayerRepositoryContractTest extends PlayerRepositoryContractTest {

	@Autowired
	private JpaPlayerRepository repository;

	@Override
	protected PlayerRepository repository() {
		return repository;
	}

}
