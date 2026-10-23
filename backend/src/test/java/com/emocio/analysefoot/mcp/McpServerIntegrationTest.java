package com.emocio.analysefoot.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import com.emocio.analysefoot.FullDataTest;
import org.springframework.context.ApplicationContext;

@FullDataTest
class McpServerIntegrationTest {

	@Autowired
	private ApplicationContext context;

	@Test
	void mcpBeans_toolCallbackProviders_exposeSearchAndComparePlayersOnce(
			@Autowired List<ToolCallbackProvider> toolCallbackProviders) {
		// the statsfoot tools (analysis tab) are exposed by the same MCP server, next to the player tools
		List<String> names = toolCallbackProviders.stream()
				.flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
				.map(callback -> callback.getToolDefinition().name())
				.toList();
		assertThat(names).doesNotHaveDuplicates();
		assertThat(names).contains("searchPlayers", "comparePlayers");
		// findPlayers and compareTeams are tools of the internal LLM only
		assertThat(names).doesNotContain("findPlayers", "compareTeams");
		assertThat(context.getBeansOfType(McpTools.class)).isEmpty();
	}


}
