package stats_foot.stats_foot.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import stats_foot.stats_foot.player.service.PlayerComparisonService;
import stats_foot.stats_foot.player.tool.PlayerTools;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** End-to-end tests of the MCP server: a real MCP client talks to the running app over Streamable HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpServerEndToEndTest {

	private static final String TOOL_NAME = "comparePlayers";
	private static final String SEARCH_TOOL_NAME = "searchPlayers";

	@LocalServerPort
	private int port;

	@Autowired
	private Environment environment;

	@Autowired
	private PlayerComparisonService playerComparisonService;

	@Autowired
	private JsonMapper jsonMapper;

	@MockitoBean
	private ChatModel chatModel;

	private McpSyncClient client;

	@BeforeEach
	void connect() {
		HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
				.builder("http://localhost:" + port)
				.endpoint("/mcp")
				.build();
		client = McpClient.sync(transport)
				.requestTimeout(Duration.ofSeconds(20))
				.initializationTimeout(Duration.ofSeconds(20))
				.build();
	}

	@AfterEach
	void disconnect() {
		client.close();
	}

	@Test
	void initialize_streamableHttp_succeeds() {
		McpSchema.InitializeResult result = client.initialize();

		assertThat(result).isNotNull();
		assertThat(result.serverInfo().name()).isNotBlank();
		assertThat(result.serverInfo().version()).isNotBlank();
		String configuredName = environment.getProperty("spring.ai.mcp.server.name");
		assertThat(configuredName).isNotBlank();
		assertThat(result.serverInfo().name()).isEqualTo(configuredName);
	}

	@Test
	void restEndpoints_withMcpEnabled_stillRespond() throws Exception {
		assertThat(statusOf("/api/players/1")).isEqualTo(200);
		assertThat(statusOf("/api/players/compare?playerA=1&playerB=2")).isEqualTo(200);
	}

	@Test
	void listTools_returnsSearchPlayersAndComparePlayers() {
		client.initialize();

		List<McpSchema.Tool> tools = client.listTools().tools();

		assertThat(tools).extracting(McpSchema.Tool::name).containsExactlyInAnyOrder(TOOL_NAME, SEARCH_TOOL_NAME,
				"searchTeams", "compareTeams");
	}

	@Test
	@SuppressWarnings("unchecked")
	void listTools_searchPlayers_hasOneRequiredStringName() {
		client.initialize();

		McpSchema.Tool tool = toolNamed(SEARCH_TOOL_NAME);
		Map<String, Object> schema = tool.inputSchema();
		Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
		List<String> required = (List<String>) schema.get("required");

		assertThat(tool.description()).isNotBlank().matches("\\A\\p{ASCII}*\\z");
		assertThat(properties).containsOnlyKeys("name");
		assertThat((Map<String, Object>) properties.get("name")).containsEntry("type", "string");
		assertThat(required).containsExactly("name");
	}

	@Test
	void searchPlayers_knownName_returnsPlayerProfile() {
		client.initialize();

		McpSchema.CallToolResult result = client
				.callTool(new McpSchema.CallToolRequest(SEARCH_TOOL_NAME, Map.of("name", "Raul RANGEL")));

		assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
		assertThat(textOf(result)).containsIgnoringCase("rangel");
	}

	@Test
	void searchPlayers_unknownName_returnsToolError() {
		client.initialize();

		McpSchema.CallToolResult result = client
				.callTool(new McpSchema.CallToolRequest(SEARCH_TOOL_NAME, Map.of("name", "qqqq nobody")));

		assertThat(result.isError()).isTrue();
	}

	private McpSchema.Tool toolNamed(String name) {
		return client.listTools().tools().stream()
				.filter(tool -> name.equals(tool.name()))
				.findFirst()
				.orElseThrow();
	}

	@Test
	void listTools_doesNotExposeInternalTools() {
		client.initialize();
		Set<String> internalNames = Arrays.stream(PlayerTools.class.getDeclaredMethods())
				.filter(method -> method.isAnnotationPresent(Tool.class))
				.map(McpServerEndToEndTest::toolName)
				.filter(name -> !TOOL_NAME.equals(name) && !SEARCH_TOOL_NAME.equals(name))
				.collect(Collectors.toSet());

		List<String> exposed = client.listTools().tools().stream().map(McpSchema.Tool::name).toList();

		assertThat(internalNames).isNotEmpty();
		assertThat(exposed).doesNotContainAnyElementsOf(internalNames);
	}

	@Test
	@SuppressWarnings("unchecked")
	void listTools_inputSchema_hasTwoRequiredStringInputs() {
		client.initialize();

		McpSchema.Tool tool = toolNamed(TOOL_NAME);
		Map<String, Object> schema = tool.inputSchema();
		Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
		List<String> required = (List<String>) schema.get("required");

		assertThat(properties).containsOnlyKeys("playerA", "playerB");
		assertThat((Map<String, Object>) properties.get("playerA")).containsEntry("type", "string");
		assertThat((Map<String, Object>) properties.get("playerB")).containsEntry("type", "string");
		assertThat(required).containsExactlyInAnyOrder("playerA", "playerB");
	}

	@Test
	@SuppressWarnings("unchecked")
	void listTools_descriptions_areNonEmptyEnglish() {
		client.initialize();

		McpSchema.Tool tool = toolNamed(TOOL_NAME);
		Map<String, Object> properties = (Map<String, Object>) tool.inputSchema().get("properties");

		assertThat(tool.description()).isNotBlank().containsIgnoringCase("player").matches("\\A\\p{ASCII}*\\z");
		for (String input : List.of("playerA", "playerB")) {
			Object description = ((Map<String, Object>) properties.get(input)).get("description");
			assertThat(description).isInstanceOf(String.class);
			assertThat((String) description).isNotBlank().containsIgnoringCase("player").matches("\\A\\p{ASCII}*\\z");
		}
	}

	@Test
	void comparePlayers_missingArgument_isRejected() {
		client.initialize();
		McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(TOOL_NAME, Map.of("playerA", "Messi"));

		// A protocol-level error (exception) or a tool error result are both valid rejections.
		boolean rejected;
		try {
			McpSchema.CallToolResult result = client.callTool(request);
			rejected = Boolean.TRUE.equals(result.isError());
		} catch (RuntimeException e) {
			rejected = true;
		}

		assertThat(rejected).isTrue();
	}

	@Test
	void comparePlayers_ids1And2Names_sameJsonAsInternalTool() throws Exception {
		client.initialize();

		McpSchema.CallToolResult result = callComparePlayers("Raul RANGEL", "Cesar MONTES");

		assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
		JsonNode actual = jsonMapper.readTree(textOf(result));
		assertThat(actual).isEqualTo(internalJson("Raul RANGEL", "Cesar MONTES"));
		assertThat(actual.get("lines")).hasSize(19);
	}

	@Test
	void comparePlayers_partialAccentlessNames_sameAsInternalTool() throws Exception {
		client.initialize();

		McpSchema.CallToolResult result = callComparePlayers("raul rangel", "cesar montes");

		assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
		assertThat(jsonMapper.readTree(textOf(result))).isEqualTo(internalJson("RAÚL rangel", "césar montes"));
	}

	@Test
	void comparePlayers_exactNameContainedInAnother_resolvesExactPlayer() throws Exception {
		client.initialize();

		McpSchema.CallToolResult result = callComparePlayers("danilo", "Raul RANGEL");

		assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
		assertThat(jsonMapper.readTree(textOf(result))).isEqualTo(internalJson("danilo", "Raul RANGEL"));
	}

	@Test
	void comparePlayers_unknownPlayerA_returnsErrorNamingPlayer() {
		client.initialize();

		McpSchema.CallToolResult result = callComparePlayers("zzzz unknown", "Raul RANGEL");

		assertThat(result.isError()).isTrue();
		assertThat(textOf(result)).contains("zzzz unknown").containsAnyOf("fuller", "corrected")
				.doesNotContain("searchPlayers");
	}

	@Test
	void comparePlayers_unknownPlayerB_returnsErrorNamingPlayerB() {
		client.initialize();

		McpSchema.CallToolResult result = callComparePlayers("Raul RANGEL", "qqqq nobody");

		assertThat(result.isError()).isTrue();
		assertThat(textOf(result)).contains("qqqq nobody").doesNotContain("searchPlayers");
	}

	@Test
	void comparePlayers_bothUnknown_errorNamesPlayerA() {
		client.initialize();

		McpSchema.CallToolResult result = callComparePlayers("xxxx", "yyyy");

		assertThat(result.isError()).isTrue();
		assertThat(textOf(result)).contains("xxxx").doesNotContain("searchPlayers");
	}

	@Test
	void comparePlayers_ambiguousName_returnsErrorWithCandidates() {
		client.initialize();

		McpSchema.CallToolResult result = callComparePlayers("a", "Raul RANGEL");

		assertThat(result.isError()).isTrue();
		assertThat(textOf(result)).contains("Several players match").contains("\"a\"")
				.contains("Please be more precise");
	}

	@Test
	void comparePlayers_blankName_returnsErrorNotNotFound() {
		client.initialize();

		for (String[] names : new String[][] { { "", "Raul RANGEL" }, { "   ", "x" } }) {
			McpSchema.CallToolResult result = callComparePlayers(names[0], names[1]);

			assertThat(result.isError()).isTrue();
			assertThat(textOf(result)).contains("must not be blank").doesNotContain("not found");
		}
	}

	@Test
	void comparePlayers_samePlayerDifferentSpelling_returnsError() {
		client.initialize();

		McpSchema.CallToolResult result = callComparePlayers("Raul RANGEL", "raúl rangel");

		assertThat(result.isError()).isTrue();
		assertThat(textOf(result)).contains("The two players must be different.");
	}

	private McpSchema.CallToolResult callComparePlayers(String playerA, String playerB) {
		return client.callTool(new McpSchema.CallToolRequest(TOOL_NAME, Map.of("playerA", playerA, "playerB", playerB)));
	}

	private static String textOf(McpSchema.CallToolResult result) {
		return ((McpSchema.TextContent) result.content().get(0)).text();
	}

	private JsonNode internalJson(String nameA, String nameB) {
		return jsonMapper.valueToTree(playerComparisonService.compareByName(nameA, nameB));
	}

	private int statusOf(String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
		return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
	}

	private static String toolName(Method method) {
		String name = method.getAnnotation(Tool.class).name();
		return name.isEmpty() ? method.getName() : name;
	}

}
