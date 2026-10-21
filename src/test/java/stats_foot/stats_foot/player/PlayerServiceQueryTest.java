package stats_foot.stats_foot.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import stats_foot.stats_foot.player.dto.PlayerQueryResult;
import stats_foot.stats_foot.player.dto.PlayerQueryResult.PlayerQueryMatch;
import stats_foot.stats_foot.player.exception.InvalidSearchException;
import stats_foot.stats_foot.player.model.ComparisonOperator;
import stats_foot.stats_foot.player.model.PlayerStat;
import stats_foot.stats_foot.player.service.PlayerService;

/** Player query of the service on the real repository (in-memory database imported from the real file). */
@SpringBootTest
class PlayerServiceQueryTest {

	@Autowired
	private PlayerService service;

	@Test
	void teamFilterMatchesNameAndCodeIgnoringCaseAndAccents() {
		PlayerQueryResult byName = service.query("  mexico ", null, null, null, null, null, true, 50);
		PlayerQueryResult byCode = service.query("MEX", null, null, null, null, null, true, 50);

		assertThat(byName.total()).isPositive();
		assertThat(byName.players()).extracting(PlayerQueryMatch::teamCode).containsOnly("MEX");
		assertThat(byCode).isEqualTo(byName);
	}

	@Test
	void unknownTeamGivesNoPlayer() {
		PlayerQueryResult result = service.query("Atlantis", null, null, null, null, null, true, null);

		assertThat(result.players()).isEmpty();
		assertThat(result.total()).isZero();
		assertThat(result.truncated()).isFalse();
	}

	@Test
	void sortDescendingPutsTheHighestFirstAndReturnsTheValue() {
		PlayerQueryResult result = service.query(null, null, null, null, null, PlayerStat.YELLOW_CARDS, true, 5);

		assertThat(result.players()).hasSize(5);
		assertThat(result.players()).extracting(PlayerQueryMatch::sortValue)
				.isSortedAccordingTo(java.util.Comparator.reverseOrder());
		assertThat(result.truncated()).isTrue();
	}

	@Test
	void sortAscendingPutsTheLowestFirst() {
		PlayerQueryResult result = service.query(null, null, null, null, null, PlayerStat.GOALS, false, 5);

		assertThat(result.players()).extracting(PlayerQueryMatch::sortValue).isSorted();
	}

	@Test
	void statFilterKeepsOnlyMatchingPlayers() {
		PlayerQueryResult result = service.query(null, null, PlayerStat.GOALS, ComparisonOperator.EQUALS, 2.0,
				PlayerStat.GOALS, true, 50);

		assertThat(result.total()).isPositive();
		assertThat(result.players()).extracting(PlayerQueryMatch::sortValue).containsOnly(2.0);
	}

	@Test
	void captainsOnlyKeepsPlayersWhoCaptained() {
		PlayerQueryResult result = service.query(null, true, null, null, null, null, true, 50);

		assertThat(result.total()).isPositive();
		assertThat(result.players()).extracting(PlayerQueryMatch::captainMatches).allMatch(n -> n > 0);
	}

	@Test
	void totalCardsIsYellowPlusRed() {
		PlayerQueryResult total = service.query(null, null, null, null, null, PlayerStat.TOTAL_CARDS, true, 1);
		PlayerQueryMatch top = total.players().get(0);
		var profile = service.getProfile(top.id()).stats();

		assertThat(top.sortValue()).isEqualTo((double) (profile.yellowCards() + profile.redCards()));
	}

	@Test
	void limitIsCappedAtFifty() {
		PlayerQueryResult result = service.query(null, null, null, null, null, null, true, 1000);

		assertThat(result.players()).hasSize(50);
		assertThat(result.truncated()).isTrue();
	}

	@Test
	void incompleteFilterIsRejected() {
		assertThatThrownBy(() -> service.query(null, null, PlayerStat.GOALS, null, 1.0, null, true, null))
				.isInstanceOf(InvalidSearchException.class)
				.hasMessage(PlayerService.INCOMPLETE_FILTER_MESSAGE);
	}

	@Test
	void nonPositiveLimitIsRejected() {
		assertThatThrownBy(() -> service.query(null, null, null, null, null, null, true, 0))
				.isInstanceOf(InvalidSearchException.class)
				.hasMessage(PlayerService.INVALID_LIMIT_MESSAGE);
	}

}
