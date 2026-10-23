# Plan: MCP server: player comparison

## Approach
Add the Spring AI MCP server starter (WebMVC, Streamable HTTP) and register one dedicated tool object in a new `stats_foot.stats_foot.mcp` package. That tool exposes `comparePlayers` and delegates to the existing `PlayerComparisonService.compareByName`, so the nominal result is the same as the internal tool (FR-4).

Main trade-off: the MCP tool is a new class, not a reuse of `PlayerTools.comparePlayers`. Reusing `PlayerTools` would expose `searchPlayers` and `queryPlayers` too (violates FR-8) and would keep the "call searchPlayers" error message (violates FR-5). The cost is a few lines of duplicated signature, while the comparison logic itself stays in the service.

`PlayerTools` and `ChatController` are not modified. Amendment: on the user's request, the error messages of `PlayerComparisonService` now name the typed player or the id (see "Error mapping"), so `McpTools` no longer has to guess which side failed.

## Components
Backend only. Root package: `stats_foot.stats_foot`.

- `pom.xml`: add `org.springframework.ai:spring-ai-starter-mcp-server-webmvc` (version managed by the `spring-ai-bom` 2.0.1 already imported). Not available in the local Maven repository: the first build needs network access.
- `src/main/resources/application.properties`: set the MCP server protocol to Streamable HTTP and give the server a name and a version. Keep the framework's default endpoint (`/mcp`). Property names to confirm against Spring AI 2.0.1 at implementation time (expected: `spring.ai.mcp.server.protocol=STREAMABLE`, `spring.ai.mcp.server.name`, `spring.ai.mcp.server.version`). The default protocol may be SSE, so the protocol property is required, not optional. This file could not be read during planning (access denied for the exploration agent and for this session): read it before editing and append only, leaving existing properties untouched.
- `mcp/McpTools.java`: plain class, **no** `@Component` (so it cannot be picked up as a second set of `@Tool` beans). One method annotated `@Tool(name = "comparePlayers", description = ...)` with two `@ToolParam` inputs `playerA`, `playerB` (mandatory, English descriptions, same wording as the internal tool for the inputs). It calls `PlayerComparisonService.compareByName`.
- `mcp/McpServerConfig.java`: `@Configuration` with one `ToolCallbackProvider` bean built with `MethodToolCallbackProvider.builder().toolObjects(new McpTools(comparisonService)).build()`. This is the only tool source the MCP server sees.
- Tests, under `src/test/java/stats_foot/stats_foot/mcp/`: `McpToolsTest` (unit) and `McpServerIntegrationTest` (`@SpringBootTest`, random port).

## Data model and contracts
- Endpoint: `POST/GET /mcp` (Streamable HTTP, framework default), no authentication.
- Tool `comparePlayers`
  - Input: `{ "playerA": string (required), "playerB": string (required) }`.
  - Output: the `PlayerComparison` record serialized as JSON by the framework (same record, same converter as the internal tool: `playerA`, `playerB`, `lines`, `summary`, `roleWarning`, `verdict`).
  - Error: a tool call that throws is returned as an MCP tool result with `isError = true` and the exception message as text content.
- Error mapping (amended): `PlayerComparisonService` now names the player in its messages: by names `Player "<typed name>" not found.` and `Several players match the name "<typed name>": ... Please be more precise.`; by ids `Player with id <id> not found.`; blank names keep `Player A|B name must not be blank.`. `McpTools` catches `ComparisonPlayerNotFoundException` only and rethrows the service message followed by `Try again with a fuller or corrected full name.`, never mentioning `searchPlayers`. Other exceptions pass through untouched. The chat tool keeps its own suffix (call `searchPlayers`).

## Spec traceability
| Requirement | Covered by |
|---|---|
| FR-1 | Starter dependency + `application.properties` (Streamable, default `/mcp`, no security); `McpServerIntegrationTest` initialize + REST still answering |
| FR-2 | `McpServerConfig` registers only `McpTools`; tool name set explicitly to `comparePlayers`; integration test on `tools/list` |
| FR-3 | `@ToolParam` on `playerA`, `playerB` (required by default); integration test on the tool input schema |
| FR-4 | Delegation to `compareByName`; unit test (`assertSame` on a mocked service) and integration test comparing the MCP content with the internal tool's JSON |
| FR-5 | Rethrow in `McpTools`; unit test (message names the player, no `searchPlayers`) and integration test (`isError = true`) |
| FR-6 | English `@Tool` and `@ToolParam` descriptions; integration test checks they are non-empty |
| FR-7 | Everything MCP in package `mcp`; `McpTools` takes `PlayerComparisonService`, no comparison logic |
| FR-8 | `PlayerTools` untouched and still the only tool source of `ChatController`; `PlayerToolsTest` and `ChatControllerTest` unchanged and green; `tools/list` has one tool |
| FR-9 | New tests above, H2 in memory via `src/test/resources/application.properties`; `./mvnw test` |

## Risks and alternatives rejected
- **Chat picks up the MCP provider (unverified).** The chat registers tools explicitly with `defaultTools(playerTools)`, and the project defines no `ToolCallbackProvider` today. It is not verified that Spring AI 2.0.1 does not add context `ToolCallbackProvider` beans to `ChatClient.Builder` automatically, which would register `comparePlayers` twice in the chat. Mitigation: check in the Spring AI 2.0.1 sources/jar during T1, and cover it with a test that builds the chat tool list. If it happens, the fix goes in `mcp` (e.g. exposing the tools through an MCP-specific provider type) before touching `ChatController`.
- **Default protocol.** SSE could be the default; the property is therefore mandatory (FR-1).
- **Property names and Jackson 3.** Spring AI 2.0.1 names and Jackson 3 (Boot 4) alignment are expected but not verified; confirm at the first build.
- **Integration test style.** Preferred: a real `McpSyncClient` with the Streamable HTTP transport against `@SpringBootTest(webEnvironment = RANDOM_PORT)`, which exercises `initialize`, `tools/list` and `tools/call` as an external client does. Fallback if the client is not on the classpath: JSON-RPC `POST /mcp` with `MockMvc` (headers `Accept: application/json, text/event-stream`, session id header). A random-port context does not share the cache of the existing mock-web tests, so the test run gets slower.
- **Existing `@SpringBootTest` classes** now load the MCP auto-configuration; `./mvnw test` must stay green with no write to `data/h2/`.
- **No security.** The open endpoint is the accepted decision (training use), documented as out of scope.
- Rejected: annotating `PlayerTools` or exposing it as a provider (leaks the other tools, keeps the `searchPlayers` message); a custom endpoint path (decision: framework default); also exposing `searchPlayers` (out of scope; revisit if strict error-message parity is wanted).
