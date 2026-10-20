package stats_foot.stats_foot.player;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

/**
 * Runs the repository contract on the JPA adapter (H2 in memory) loaded from the real players, and checks
 * that the service gives the same results on the JSON and JPA adapters.
 */
@SpringBootTest(properties = {
		"statsfoot.player-source=database",
		"spring.datasource.url=jdbc:h2:mem:contracttest;DB_CLOSE_DELAY=-1",
		"spring.jpa.hibernate.ddl-auto=create-drop" })
@ActiveProfiles("database")
class JpaPlayerRepositoryContractTest extends PlayerRepositoryContractTest {

	private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-20T10:00:00Z"), ZoneOffset.UTC);

	@Autowired
	private JpaPlayerRepository jpaRepository;

	@Autowired
	private PlayerJpaRepository playerJpaRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@DynamicPropertySource
	static void dataDir(DynamicPropertyRegistry registry) {
		registry.add("statsfoot.data-dir", () -> TestDataFiles.dataDir().toString());
	}

	@BeforeEach
	void loadRealPlayers() {
		if (playerJpaRepository.count() > 0) {
			return;
		}
		List<Player> players = jsonRepository().findAll();
		Map<String, TeamEntity> teams = new LinkedHashMap<>();
		players.forEach(p -> teams.computeIfAbsent(p.team().id(), id -> TeamEntity.from(p.team())));
		transactionTemplate.executeWithoutResult(status -> teams.values().forEach(entityManager::persist));
		playerJpaRepository.saveAll(players.stream().map(p -> PlayerEntity.from(p, teams.get(p.team().id()))).toList());
	}

	@Override
	protected PlayerRepository repository() {
		return jpaRepository;
	}

	@ParameterizedTest
	@ValueSource(strings = { "suz", "sami suzuki", "ou", "id", "a", "xyzxyz", "ramirez" })
	void searchGivesSameResultsAsJsonAdapter(String query) {
		PlayerSearchResult expected = new PlayerService(jsonRepository(), FIXED_CLOCK).search(query);
		PlayerSearchResult actual = new PlayerService(jpaRepository, FIXED_CLOCK).search(query);
		assertThat(actual.players().stream().map(PlayerSummary::id).toList())
				.isEqualTo(expected.players().stream().map(PlayerSummary::id).toList());
		assertThat(actual.total()).isEqualTo(expected.total());
		assertThat(actual.truncated()).isEqualTo(expected.truncated());
	}

	@ParameterizedTest
	@ValueSource(strings = { "P-0001", "P-0003", "P-0004", "P-0005", "P-0020" })
	void profileEqualsJsonAdapterProfile(String id) {
		PlayerProfile expected = new PlayerService(jsonRepository(), FIXED_CLOCK).getProfile(id);
		PlayerProfile actual = new PlayerService(jpaRepository, FIXED_CLOCK).getProfile(id);
		assertThat(actual).isEqualTo(expected);
	}

	@Test
	void searchNameColumnHoldsNormalisedName() {
		assertThat(playerJpaRepository.findById("P-0004").orElseThrow().getSearchName()).isEqualTo("kwame ramirez");
	}

	private static JsonPlayerRepository jsonRepository() {
		return new JsonPlayerRepository(TestDataFiles.dataDir().toString());
	}

}
