package com.emocio.analysefoot.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.emocio.analysefoot.player.service.PlayerComparisonService;
import com.emocio.analysefoot.player.tool.PlayerTools;

/** Registers the tools exposed by the MCP server; McpTools is deliberately not a bean. */
@Configuration
public class McpServerConfig {

	@Bean
	public ToolCallbackProvider mcpToolCallbackProvider(PlayerComparisonService playerComparisonService,
			PlayerTools playerTools) {
		return MethodToolCallbackProvider.builder()
				.toolObjects(new McpTools(playerComparisonService, playerTools))
				.build();
	}

}
