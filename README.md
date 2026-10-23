# statsFoot

Sports analysis of the 2026 World Cup. Current features: **player profile search** — type a player name, get the player's team and tournament statistics (minutes, goals, cards, duels, distance, ratings...) — and **player comparison** — type two player names, press one button and get a side-by-side report with the leader on each statistic and a written verdict (page `/compare`) — and **team comparison** — same principle for two national teams (page `/compare-teams`).

## Requirements

- Java 21 (Maven is provided by the wrapper `./mvnw`)
- Node.js and npm (for the Angular FrontEnd in `frontend/`)

## Data

The tournament data comes from `data/donneesStats.json` (48 teams, 1039 players, 104 matches). The schema of the database is described by `data/donneesStats.sql` (PostgreSQL reference script, not executed by the application) and ported to H2 in `src/main/resources/db/schema.sql`.

At startup the application creates an **H2 file database** (`data/h2/statsfoot.mv.db`, not versioned) and fills it from `donneesStats.json` when it is empty. Later starts reuse the database. To reload the data, stop the application and delete the folder `data/h2/`.

If the database or the import fails, the application still starts and the API answers `503`.

## Run

```
./mvnw spring-boot:run     # BackEnd on http://localhost:8080 (first start creates and fills data/h2/)
cd frontend
npm install                # first time only
npm start                  # FrontEnd on http://localhost:4200 (proxies /api to :8080)
```

## Tests

```
./mvnw test                # BackEnd (in-memory H2, never writes data/h2/)
cd frontend && npm test    # FrontEnd (non interactive)
```

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `statsfoot.database-file` | `./data/h2/statsfoot` | H2 database file (without extension). H2 needs an explicit `./` or an absolute path. |
| `statsfoot.data-file` | `data/donneesStats.json` | Data file imported when the database is empty |
| `spring.datasource.url` | H2 file URL built from `statsfoot.database-file` | Datasource (tests use an in-memory H2) |
| `spring.ai.mcp.server.protocol` | `SSE` (framework default) | Must be `STREAMABLE` for the MCP server to answer on `/mcp` (see "MCP server") |
| `spring.ai.mcp.server.name` / `version` | `mcp-server` / `1.0.0` | Server name and version seen by MCP clients (`statsfoot-mcp` / `1.0.0` here) |

The `spring.ai.mcp.server.*` properties are set in both `src/main/resources/application.properties` and `src/test/resources/application.properties`: the test file replaces the main one on the classpath, so the tests would not see the MCP configuration otherwise.

## API

- `GET /api/players?name=<text>` — search by name (case, accents and extra spaces ignored, partial match). Returns `{ total, truncated, players[] }` with `id`, `name`, `teamCode`, `teamName`; at most 50 players sorted by name. `400` if the name is blank or missing.
- `GET /api/players/{id}` — profile: name, team, goalkeeper and captain match counts and the tournament totals (`stats`: matches, minutes, goals, cards, fouls, free kicks, duels, corners, offsides, distance, man of the match, ratings). A statistic without value is `null`. `404` if the id is unknown or not a number.
- `GET /api/players/compare?playerA=<id>&playerB=<id>` — comparison report of two different players. Returns `playerA` / `playerB` (`id`, `name`, `teamName`, `teamCode`, `role`: `GOALKEEPER` if the player played at least one match as goalkeeper, else `OUTFIELD_PLAYER`), `lines[]` (one per statistic: `key`, `label`, `valueA`, `valueB`, `difference` = A − B, `leader`: `A`, `B`, `TIE` or `null`, `available`), `summary` (`leadsA`, `leadsB`, `ties`), `roleWarning` (`null` when both roles are the same) and `verdict` (deterministic English text). `400` if the two ids are equal, missing or not integers; `404` with `Player with id <id> not found.` for an unknown id (A is checked before B).
- `GET /api/teams?name=<text>` — search teams by name (case, accents and extra spaces ignored, partial match) or exact FIFA code. Returns `{ total, teams[] }` with `id`, `name`, `code`. `400` `Please enter a team name.` if the name is blank or missing.
- `GET /api/teams/compare?teamA=<id>&teamB=<id>` — comparison report of two different teams. Returns `teamA` / `teamB` (`id`, `name`, `code`), `lines[]`, `summary` and `verdict` (same structure and rules as the player comparison, without role warning). `400` if the two ids are equal, missing or not integers; `404` with `Team A not found.` / `Team B not found.` for an unknown id.
- `503` with `{ "error": "Player data is currently unavailable." }` when the database cannot be read or the import failed.

### Player comparison

Rules of the report (`PlayerComparisonService`):

- Statistics where less is better (own goals, yellow and red cards, fouls committed, free kicks conceded, duels lost, offsides): the lowest value leads. Matches played, matches started, minutes played and distance covered are shown with their difference but never designate a leader. Every other statistic: the highest value leads.
- A statistic missing for one of the two players is `available: false`, with no difference and no leader, and is not counted in the summary.
- `difference` is rounded to 2 decimals and the leader is decided on that rounded difference (a null difference gives `TIE`).
- The verdict names who leads on more statistics and cites at most three main differences (largest relative gap `|A − B| / max(|A|, |B|)`), then repeats the role warning when the roles differ.
- Example: `curl "http://localhost:8080/api/players/compare?playerA=1&playerB=2"`.

`PlayerComparisonService` is a plain Spring bean without any HTTP dependency, so it can be called directly from Java code (for instance by an LLM tool that does not know player ids):

- `compare(int idA, int idB)` throws `SamePlayerComparisonException`, `ComparisonPlayerNotFoundException` and `PlayerDataUnavailableException`.
- `compareByName(String nameA, String nameB)` resolves each name (A first, then B) with the same search as `GET /api/players?name=` (case, accents and extra spaces ignored, partial match), then behaves like `compare`. It additionally throws `BlankComparisonNameException` (blank name), `ComparisonPlayerNotFoundException` (`Player "<typed name>" not found.`) and `AmbiguousPlayerNameException` when a name matches several players and none or several of them have a name exactly equal to the given one (exact match first: if exactly one does, case, accents and extra spaces ignored, that player is used, e.g. `danilo` gives `DANILO` even though `DANILO SANTOS` also matches; message `Several players match the name "<typed name>": ...` listing at most 10 candidates with their team, then `and N more` if any). There is no HTTP endpoint by names.

The FrontEnd page `/compare` (menu "Compare") has two text fields and a single "Compare" button (Enter also submits). On "Compare" each name is resolved with the search endpoint: a blank field shows "Please enter a player name.", an unknown name shows "Player not found.", and a name matching several players lists them under the field (click one, then press "Compare" again), unless exactly one of them has a name equal to the typed text (case, accents and extra spaces ignored): that player is then used directly. Only the first 50 results of the search are examined for the exact match. Once both players are identified the page shows both identities, the role warning, the summary, the verdict and one row per statistic with a bar on the side of the leading player (bar width = relative gap). Editing a field clears its chosen player, its candidates, its error and the report. Comparing a player with themselves shows the API error.

### Team comparison

`TeamComparisonService.compare(int idA, int idB)` (no HTTP dependency, throws `SameTeamComparisonException`, `TeamNotFoundException` and `PlayerDataUnavailableException`) reuses `ComparisonLines` and `ComparisonVerdict` of the player comparison. The totals come from the view `v_team_tournament_stats` (built from `team_match_rating`, `team_match_stats` and `booking`). Statistics: matches played, wins, draws, losses, points, goals for / against, yellow and red cards, fair-play points, fouls committed, free kicks won / conceded, duels won / lost, corners, offsides, distance, rating. Less is better for losses, goals against, cards, fouls committed, free kicks conceded, duels lost and offsides; matches played, draws and distance never designate a leader. Totals are not normalised per match: a team that played more matches accumulates more.

The FrontEnd page `/compare-teams` (menu "Compare teams") works like the player page: two fields, one "Compare" button, candidates listed when several teams match (exact name first), bars on the side of the leader.

## MCP server

The application is also a Model Context Protocol (MCP) server, so that an external MCP client (Claude Desktop, Claude Code, an IDE assistant...) can use the player comparison. It is independent of the chat assistant, whose own tools (`PlayerTools`) are unchanged and not exposed through MCP.

- **Endpoint**: `http://localhost:8080/mcp`, Streamable HTTP, framework default path. The endpoint is open (no authentication): training use only.
- **Tool**: exactly one, `comparePlayers`, with two mandatory text inputs `playerA` and `playerB` (full or partial name, case and accents ignored). It returns the same JSON as the chat's `comparePlayers` tool (`PlayerComparison`: `playerA`, `playerB`, `lines`, `summary`, `roleWarning`, `verdict`).
- **Errors**: returned as a tool result with `isError = true` and a text message. The messages come from `PlayerComparisonService` and name the typed player: `Player "<typed name>" not found.`, `Several players match the name "<typed name>": ... Please be more precise.`, `Player A name must not be blank.`, `The two players must be different.` (for two unknown players, only the first one is reported). For an unknown player the MCP tool adds `Try again with a fuller or corrected full name.`; the chat tool adds instead the advice to call `searchPlayers`, a tool MCP clients do not have.
- **Connection example** (Claude Code): `claude mcp add --transport http statsfoot http://localhost:8080/mcp`.

How it works (package `stats_foot.stats_foot.mcp`):

- `McpTools` is a plain class (not a Spring bean) with one `@Tool` method that delegates to `PlayerComparisonService.compareByName`. On `ComparisonPlayerNotFoundException` it rethrows the service message (`Player "<typed name>" not found.`) followed by the advice to retry with a fuller or corrected name.
- `McpServerConfig` registers a single `ToolCallbackProvider` containing only that object. The Spring AI MCP server exposes every `ToolCallbackProvider` of the context, so `PlayerTools` must never be registered as one, or the other chat tools would leak to MCP clients.

Tests: `McpToolsTest` (unit), `McpServerIntegrationTest` (providers registered, no duplicated tool in the chat) and `McpServerEndToEndTest` (real MCP client on a random port: initialize, tool list and schema, nominal calls compared with the internal service, error cases).

Known limits: only the comparison is exposed (no search, no query, no team comparison); the endpoint is not protected.

The specifications, plans and tasks are in `specs/` (`player-profile-search`, amended by `player-h2-database`; `player-comparison`; `mcp-player-comparison`).
