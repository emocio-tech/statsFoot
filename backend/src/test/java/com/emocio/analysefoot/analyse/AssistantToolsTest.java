package com.emocio.analysefoot.analyse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.emocio.analysefoot.FullDataTest;

/** The read tools of the internal LLM: the statistics tools and the player and team tools, each name once. */
@FullDataTest
class AssistantToolsTest {

	@Autowired
	private BeanFactory beanFactory;

	@Test
	void assistantHasAllToolsOnce() {
		List<String> names = AssistantConfig.dataTools(beanFactory).stream()
				.map(callback -> callback.getToolDefinition().name())
				.toList();

		assertThat(names).doesNotHaveDuplicates();
		assertThat(names).contains("metrics", "rank", "stat", "find", "player_report", "team_report", "matches",
				"calculate", "searchPlayers", "findPlayers", "comparePlayers", "compareTeams");
	}

}
