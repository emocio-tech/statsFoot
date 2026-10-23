# MCP server: player comparison

## Context and goal
The application already lets its built-in chat assistant compare two World Cup 2026 players through an internal tool. That tool is only usable by this application's own assistant.

The goal is to also expose the same capability through the Model Context Protocol (MCP), so that any external MCP client (e.g. Claude Desktop, Claude Code, an IDE assistant) can ask the application to compare two players. The existing internal tools and the chat assistant must keep working unchanged. The MCP part lives in its own `mcp` area of the application, separate from the existing tool code.

Target user: a casual user who wants information on World Cup 2026 players and teams, now also from the AI client of their choice.

## User stories
- As a user of an MCP client, I want to connect to the application's MCP server so that I can discover the tools it offers.
- As a user of an MCP client, I want to compare two players by name so that I get the same comparison report as in the application's chat.
- As a user of an MCP client, I want a clear error when a player is not found so that I can correct the name and retry.
- As a maintainer, I want the existing chat tools untouched so that the chat assistant behaves exactly as before.

## Functional requirements
- FR-1: The application must expose an MCP server reachable over Streamable HTTP at the framework's default endpoint, with no authentication, while the existing REST endpoints and the chat keep working.
- FR-2: The MCP server must advertise exactly one tool, a player comparison tool named `comparePlayers`, in its tool list.
- FR-3: The MCP comparison tool must take the same two inputs as the existing comparison tool (the full or partial name of player A and of player B), both mandatory.
- FR-4: For the same two inputs, the MCP comparison tool must return the same comparison content as the existing comparison tool (same data, same fields, same rules for name matching).
- FR-5: When one of the players cannot be found, the MCP tool must return an error that names the missing player and tells the caller how to proceed. Since the lookup tool is not exposed through MCP, the message must suggest retrying with a fuller or corrected name, not refer to a tool the client cannot call.
- FR-6: The tool description visible to MCP clients must explain, in English, what the tool does and what each input means.
- FR-7: All MCP-specific code must live in a dedicated `mcp` package, and the MCP tool must reuse the existing comparison logic rather than duplicate it.
- FR-8: The existing internal tools (player search, player query, player comparison) must remain unchanged in behaviour and remain available to the chat assistant only. They must not appear in the MCP tool list.
- FR-9: The MCP feature must be covered by automated tests, run against the in-memory test database.

## Acceptance criteria
- AC-1 (FR-1): Given the application is running, When an MCP client connects to the MCP endpoint and initializes a session, Then the connection succeeds and the existing REST endpoints still respond as before.
- AC-2 (FR-2): Given a connected MCP client, When it lists the tools, Then it receives exactly one tool, named `comparePlayers`.
- AC-3 (FR-3): Given the tool list, When the client reads the tool's input definition, Then it shows two mandatory text inputs, one per player.
- AC-4 (FR-4): Given two existing players, When the client calls the MCP tool with their names, Then the response contains the same comparison as the existing internal tool returns for the same names.
- AC-5 (FR-4): Given partial or accent-less names matching a single player each, When the client calls the tool, Then the comparison is returned as with the internal tool.
- AC-6 (FR-5): Given one unknown player name, When the client calls the tool, Then the call is reported as an error naming the unknown player and suggesting retrying with a fuller or corrected name, with no mention of the search tool.
- AC-7 (FR-6): Given the tool list, When the client reads the descriptions, Then the tool and both inputs have a non-empty English description.
- AC-8 (FR-7): Given the source code, When reviewing the MCP classes, Then they are all in the `mcp` package and delegate comparison to the existing comparison service.
- AC-9 (FR-8): Given the chat assistant, When the existing tool tests and chat flows run, Then they pass unchanged, and the MCP tool list contains none of the three existing tools.
- AC-10 (FR-9): Given the test suite, When `./mvnw test` is run, Then the MCP tests pass and never write to `data/h2/`.

## Out of scope
- Exposing the player search and player query tools through MCP (only the comparison tool for now).
- Exposing team comparison, or any data outside World Cup 2026 (other championships, other years, cross-championship comparison).
- Authentication, authorization and rate limiting of the MCP endpoint (open endpoint, training use only).
- MCP resources and prompts (tools only).
- Changes to the Angular front end.
- Turning the chat assistant itself into an MCP client.

## Decisions
- Transport: Streamable HTTP, on a single endpoint.
- Endpoint path: the framework's default path (no custom path).
- Tool name seen by MCP clients: `comparePlayers`, identical to the internal tool.
- Access: the endpoint is open, with no authentication (training project, local use).
- "Identical" means same name, same inputs and same output: the same request must give the same result whether it comes from the application's chat or from an external chat connected to the MCP server. The only difference is the advice appended to the not-found message (FR-5): the MCP tool suggests a fuller or corrected name, the chat tool suggests calling the search tool, which MCP clients do not have. Amended: the service messages themselves now name the typed player (or the id for a comparison by ids) everywhere.

## Open questions
- None.
