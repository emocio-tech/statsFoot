package stats_foot.stats_foot.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import stats_foot.stats_foot.FullDataTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import stats_foot.stats_foot.player.tool.PlayerTools;

@FullDataTest
@AutoConfigureMockMvc
class McpServerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ApplicationContext context;

	@MockitoBean
	private ChatModel chatModel;

	@Test
	void chat_toolList_hasNoDuplicateComparePlayers() throws Exception {
		when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
		when(chatModel.call(any(Prompt.class)))
				.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("ok")))));

		mockMvc.perform(post("/api/chat").contentType(MediaType.TEXT_PLAIN).content("Hello"))
				.andExpect(status().isOk());

		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		verify(chatModel).call(promptCaptor.capture());
		ToolCallingChatOptions options = (ToolCallingChatOptions) promptCaptor.getValue().getOptions();
		List<String> toolNames = options.getToolCallbacks().stream()
				.map(callback -> callback.getToolDefinition().name())
				.toList();

		Set<String> expectedNames = Arrays.stream(PlayerTools.class.getDeclaredMethods())
				.filter(method -> method.isAnnotationPresent(Tool.class))
				.map(McpServerIntegrationTest::toolName)
				.collect(Collectors.toSet());
		assertThat(toolNames).doesNotHaveDuplicates();
		assertThat(toolNames).filteredOn("comparePlayers"::equals).hasSize(1);
		assertThat(toolNames).containsExactlyInAnyOrderElementsOf(expectedNames);
	}

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
		assertThat(context.getBeansOfType(McpTools.class)).isEmpty();
	}

	private static String toolName(Method method) {
		String name = method.getAnnotation(Tool.class).name();
		return name.isEmpty() ? method.getName() : name;
	}

}
