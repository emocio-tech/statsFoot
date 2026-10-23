package stats_foot.stats_foot.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import stats_foot.stats_foot.FullDataTest;

import stats_foot.stats_foot.player.exception.AmbiguousPlayerNameException;
import stats_foot.stats_foot.player.exception.ComparisonPlayerNotFoundException;
import stats_foot.stats_foot.player.service.PlayerComparisonService;

/** By-name comparison on the real application (real imported data). */
@FullDataTest
class PlayerComparisonByNameTest {

	@Autowired
	private PlayerComparisonService comparisonService;

	@Test
	void knownNames_withOtherCaseAndAccents_giveTheReportOfTheIds() {
		// Players 1 and 2 are "Raul RANGEL" and "Cesar MONTES".
		assertThat(comparisonService.compareByName("RAÚL rangel", "césar montes"))
				.isEqualTo(comparisonService.compare(1, 2));
	}

	@Test
	void exactNameContainedInAnotherName_usesTheExactPlayer() {
		// Player 182 is "DANILO", player 184 is "DANILO SANTOS": the search returns both.
		assertThat(comparisonService.compareByName("danilo", "Raul RANGEL"))
				.isEqualTo(comparisonService.compare(182, 1));
	}

	@Test
	void unknownName_raisesNotFound() {
		assertThatThrownBy(() -> comparisonService.compareByName("Raul RANGEL", "zzzz unknown"))
				.isInstanceOf(ComparisonPlayerNotFoundException.class)
				.hasMessage("Player \"zzzz unknown\" not found.");
	}

	@Test
	void veryShortCommonSubstring_raisesAmbiguousWithTenCandidates() {
		assertThatThrownBy(() -> comparisonService.compareByName("a", "Raul RANGEL"))
				.isInstanceOf(AmbiguousPlayerNameException.class)
				.satisfies(e -> {
					String message = e.getMessage();
					assertThat(message).startsWith("Several players match the name \"a\": ");
					assertThat(message).containsPattern(" and \\d+ more\\. Please be more precise\\.$");
					String list = message.substring(message.indexOf(": ") + 2, message.lastIndexOf(" and "));
					assertThat(list.split("\\), ")).hasSize(10);
				});
	}

}
