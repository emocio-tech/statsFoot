package stats_foot.stats_foot.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import stats_foot.stats_foot.player.service.PlayerComparisonService;
import stats_foot.stats_foot.player.tool.PlayerTools;
import stats_foot.stats_foot.tool.StatTools;
import stats_foot.stats_foot.team.tool.TeamTools;

/** Registers the tools exposed by the MCP server; McpTools and McpTeamTools are deliberately not beans. */
@Configuration
public class McpServerConfig {

	@Bean
	public ToolCallbackProvider mcpToolCallbackProvider(PlayerComparisonService playerComparisonService,
			PlayerTools playerTools, StatTools statTools) {
		return MethodToolCallbackProvider.builder()
				.toolObjects(new McpTools(playerComparisonService, playerTools), new McpTeamTools(teamTools), statTools)
				.build();
	}

}
