# statsFoot

Sports analysis of the 2026 World Cup. Current features: **player profile search** — type a player name, get the player's team and tournament statistics (minutes, goals, cards, duels, distance, ratings...) — and **player comparison** — type two player names, press one button and get a side-by-side report with the leader on each statistic and a written verdict (page `/compare`).

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

## API

- `GET /api/players?name=<text>` — search by name (case, accents and extra spaces ignored, partial match). Returns `{ total, truncated, players[] }` with `id`, `name`, `teamCode`, `teamName`; at most 50 players sorted by name. `400` if the name is blank or missing.
- `GET /api/players/{id}` — profile: name, team, goalkeeper and captain match counts and the tournament totals (`stats`: matches, minutes, goals, cards, fouls, free kicks, duels, corners, offsides, distance, man of the match, ratings). A statistic without value is `null`. `404` if the id is unknown or not a number.
- `GET /api/players/compare?playerA=<id>&playerB=<id>` — comparison report of two different players. Returns `playerA` / `playerB` (`id`, `name`, `teamName`, `teamCode`, `role`: `GOALKEEPER` if the player played at least one match as goalkeeper, else `OUTFIELD_PLAYER`), `lines[]` (one per statistic: `key`, `label`, `valueA`, `valueB`, `difference` = A − B, `leader`: `A`, `B`, `TIE` or `null`, `available`), `summary` (`leadsA`, `leadsB`, `ties`), `roleWarning` (`null` when both roles are the same) and `verdict` (deterministic English text). `400` if the two ids are equal, missing or not integers; `404` with `Player A not found.` / `Player B not found.` for an unknown id.
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
- `compareByName(String nameA, String nameB)` resolves each name (A first, then B) with the same search as `GET /api/players?name=` (case, accents and extra spaces ignored, partial match), then behaves like `compare`. It additionally throws `BlankComparisonNameException` (blank name), `ComparisonPlayerNotFoundException` (`Player A not found.` / `Player B not found.`) and `AmbiguousPlayerNameException` when a name matches several players and none or several of them have a name exactly equal to the given one (exact match first: if exactly one does, case, accents and extra spaces ignored, that player is used, e.g. `danilo` gives `DANILO` even though `DANILO SANTOS` also matches; message listing at most 10 candidates with their team, then `and N more` if any). There is no HTTP endpoint by names.

The FrontEnd page `/compare` (menu "Compare") has two text fields and a single "Compare" button (Enter also submits). On "Compare" each name is resolved with the search endpoint: a blank field shows "Please enter a player name.", an unknown name shows "Player not found.", and a name matching several players lists them under the field (click one, then press "Compare" again), unless exactly one of them has a name equal to the typed text (case, accents and extra spaces ignored): that player is then used directly. Only the first 50 results of the search are examined for the exact match. Once both players are identified the page shows both identities, the role warning, the summary, the verdict and one row per statistic with a bar on the side of the leading player (bar width = relative gap). Editing a field clears its chosen player, its candidates, its error and the report. Comparing a player with themselves shows the API error.

The specifications, plans and tasks are in `specs/` (`player-profile-search`, amended by `player-h2-database`; `player-comparison`).
