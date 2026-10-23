package com.emocio.analysefoot.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import com.emocio.analysefoot.player.model.PlayerStat;

/** The statistics of the LLM tools (stat, rank) and those of the player search and comparison are the same set. */
class StatMetricsConsistencyTest {

	@Test
	void playerStatEnumCoversExactlyTheCatalogue() {
		List<String> enumNames = Arrays.stream(PlayerStat.values())
				.map(stat -> stat.name().toLowerCase(Locale.ROOT))
				.toList();

		assertThat(enumNames).containsExactlyInAnyOrderElementsOf(StatMetrics.names(true));
	}

	@Test
	void statToolDescriptionListsEveryMetric() throws Exception {
		String description = StatTools.class
				.getMethod("stat", String.class, String.class, String.class, String.class, String.class, String.class,
						Integer.class)
				.getAnnotation(org.springframework.ai.tool.annotation.Tool.class).description();

		assertThat(StatMetrics.allNames()).allSatisfy(name -> assertThat(description).contains(name));
	}

	@Test
	void totalCardsAddsYellowAndRedCards() {
		assertThat(StatMetrics.expression(true, "total_cards")).isEqualTo("yellow_cards + red_cards");
		assertThat(StatMetrics.expression(true, "captain_matches")).isEqualTo("captain_matches");
		assertThat(StatMetrics.expression(false, "total_cards")).isNull();
	}

}
