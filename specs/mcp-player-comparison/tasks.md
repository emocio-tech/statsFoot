# Tasks: MCP server: player comparison

Test IDs (U-xx, M-xx, R-xx, S-xx) refer to the test plan derived from the acceptance criteria. Test method names follow `method_scenario_result`.

- [x] T1 (FR-1, FR-8, AC-1, AC-9) Add the MCP starter and check it does not leak into the chat
  - Files: `pom.xml`, Spring AI 2.0.1 jar sources (read only)
  - Steps: add `spring-ai-starter-mcp-server-webmvc` (version from the BOM); build; confirm in the Spring AI 2.0.1 code whether `ChatClient.Builder` auto-registers context `ToolCallbackProvider` beans, and write the finding in a comment of the PR/commit message, not in the code.
  - Done when: `./mvnw test` still passes with the starter on the classpath and the answer on chat auto-registration is known (if it does register them, stop and amend `plan.md` before T4).

- [x] T2 (FR-1, AC-1) Configure the MCP server properties
  - Files: `src/main/resources/application.properties` (read it first, append only; not readable by the agents so far, needs the user's permission or pasted content)
  - Steps: set the protocol to Streamable HTTP, a server name and a version; keep the default endpoint `/mcp`; confirm exact property names for 2.0.1.
  - Done when: the application starts with `./mvnw spring-boot:run` and answers an MCP `initialize` on `/mcp`; existing properties are unchanged.

- [x] T3 (FR-2, FR-3, FR-4, FR-6, FR-7, AC-2, AC-3, AC-4, AC-5, AC-7, AC-8) Unit tests then `McpPlayerTools` nominal path
  - Files: `src/test/java/stats_foot/stats_foot/mcp/McpPlayerToolsTest.java`, `src/main/java/stats_foot/stats_foot/mcp/McpPlayerTools.java`
  - Tests: U-01 `comparePlayers_existingPlayers_delegatesToService`, U-02 `comparePlayers_partialAccentlessNames_passesNamesUnchanged`, U-10 `class_isInMcpPackageAndNotAComponent`, U-11 `comparePlayers_toolAnnotation_hasNameAndDescriptions`, U-12 `mcpClasses_onlyDependOnComparisonService`.
  - Code: plain class (no `@Component`), constructor takes `PlayerComparisonService`, one `@Tool(name = "comparePlayers", ...)` method with two mandatory `@ToolParam` inputs `playerA`, `playerB`, English descriptions, delegating to `compareByName`.
  - Done when: `./mvnw test -Dtest=McpPlayerToolsTest` is green.

- [x] T4 (FR-5, AC-6) Not-found error message for MCP clients
  - Files: `McpPlayerToolsTest.java`, `McpPlayerTools.java`
  - Tests: U-03 `comparePlayers_unknownPlayerA_namesPlayerAndSuggestsFullerName`, U-04 `comparePlayers_unknownPlayerB_namesPlayerBNotPlayerA`, U-05 `comparePlayers_bothUnknown_reportsPlayerAOnly`, U-06 blank name keeps message, U-07 ambiguous name keeps message, U-08 same player keeps exception, U-09 empty name never reports not found.
  - Code: catch `ComparisonPlayerNotFoundException` only, rethrow the service message (which now names the typed player) followed by a suggestion of a fuller or corrected name, never mentioning `searchPlayers`; other exceptions pass through untouched. Amended: the service messages name the typed player or the id (`PlayerComparisonService.resolve` and `compare`).
  - Done when: `./mvnw test -Dtest=McpPlayerToolsTest` is green. Assertions rely on stable fragments (typed name, "fuller" or "corrected", no "searchPlayers").

- [x] T5 (FR-2, FR-7, FR-8, AC-2, AC-8, AC-9) Register the MCP-only tool provider
  - Files: `src/main/java/stats_foot/stats_foot/mcp/McpServerConfig.java`, `src/test/java/stats_foot/stats_foot/mcp/McpServerIntegrationTest.java` (first tests)
  - Tests: M-18 `mcpBeans_onlyOneToolCallbackProvider_exposesComparePlayersOnly`, M-17 `chat_toolList_hasNoDuplicateComparePlayers` (written before the config is added, must stay green after it).
  - Code: one `@Configuration` with one `ToolCallbackProvider` built by `MethodToolCallbackProvider` from a `McpPlayerTools` instance.
  - Done when: both tests pass and `PlayerTools`, `ChatController`, `PlayerComparisonService` have no diff (R-03).

- [x] T6 (FR-1, FR-2, FR-3, FR-6, AC-1, AC-2, AC-3, AC-7, AC-9) End-to-end tests: connection and tool list
  - Files: `McpServerEndToEndTest.java` (`@SpringBootTest(webEnvironment = RANDOM_PORT)`, real `McpSyncClient` over Streamable HTTP, fallback JSON-RPC via `MockMvc`)
  - Tests: M-01 `initialize_streamableHttp_succeeds`, M-02 `restEndpoints_withMcpEnabled_stillRespond`, M-03 `listTools_returnsExactlyComparePlayers`, M-04 `listTools_doesNotExposeInternalTools`, M-15 `listTools_inputSchema_hasTwoRequiredStringInputs`, M-16 `listTools_descriptions_areNonEmptyEnglish`, M-14 `comparePlayers_missingArgument_isRejected`.
  - Done when: these tests are green; exact internal tool names are taken from `PlayerTools`, not guessed.

- [x] T7 (FR-4, FR-5, AC-4, AC-5, AC-6) End-to-end tests: tool calls
  - Files: `McpServerEndToEndTest.java`
  - Tests: M-05 `comparePlayers_ids1And2Names_sameJsonAsInternalTool` (same JSON as the internal tool for the same names), M-06 partial and accent-less names, M-07 exact name contained in another, M-08 unknown player A, M-09 unknown player B, M-10 both unknown, M-11 ambiguous name, M-12 blank name (framework behaviour to confirm at first run, then fix the expected result), M-13 same player.
  - Done when: all pass; error cases return `isError = true` and never contain `searchPlayers`.

- [x] T8 (FR-8, FR-9, AC-9, AC-10) Non-regression and full suite
  - Files: none to create
  - Steps: R-01 `PlayerToolsTest` and R-02 `ChatControllerTest` unchanged and green; R-04 `PlayerComparisonControllerTest`, `PlayerComparisonByNameTest`, `StatsFootApplicationTests` green; S-02 check that tests use the in-memory H2 (`data/h2/` untouched).
  - Done when: `./mvnw test` is BUILD SUCCESS, `McpPlayerToolsTest` and `McpServerIntegrationTest` are executed (not skipped), `git diff` shows no change in `PlayerTools`, `ChatController`, `PlayerComparisonService` or existing tests.

- [x] T9 (FR-1, FR-2, FR-8) Document the MCP endpoint
  - Files: project documentation (to be generated with the `doc-generator` skill after implementation)
  - Done when: the doc states the endpoint (`/mcp`, Streamable HTTP, open access), the single `comparePlayers` tool, its inputs, and the difference in the not-found message versus the chat.
