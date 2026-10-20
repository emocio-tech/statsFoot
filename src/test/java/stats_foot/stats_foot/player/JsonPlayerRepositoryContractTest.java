package stats_foot.stats_foot.player;

/** Runs the repository contract on the JSON adapter with the real data files. */
class JsonPlayerRepositoryContractTest extends PlayerRepositoryContractTest {

	@Override
	protected PlayerRepository repository() {
		return new JsonPlayerRepository(TestDataFiles.dataDir().toString());
	}

}
