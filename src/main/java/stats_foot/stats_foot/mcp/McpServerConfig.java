package stats_foot.stats_foot.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import stats_foot.stats_foot.player.service.PlayerComparisonService;

/** Registers the tools exposed by the MCP server; McpPlayerTools is deliberately not a bean. */
@Configuration
public class McpServerConfig {

	@Bean
	public ToolCallbackProvider mcpToolCallbackProvider(PlayerComparisonService playerComparisonService) {
		return MethodToolCallbackProvider.builder()
				.toolObjects(new McpTools(playerComparisonService))
				.build();
	}

}
