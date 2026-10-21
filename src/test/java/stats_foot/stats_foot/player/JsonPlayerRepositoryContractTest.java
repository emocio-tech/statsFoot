package stats_foot.stats_foot.player;

import stats_foot.stats_foot.player.repository.JsonPlayerRepository;
import stats_foot.stats_foot.player.repository.PlayerRepository;

/** Runs the repository contract on the JSON adapter with the real data files. */
class JsonPlayerRepositoryContractTest extends PlayerRepositoryContractTest {

	@Override
	protected PlayerRepository repository() {
		return new JsonPlayerRepository(TestDataFiles.dataDir().toString());
	}

}
