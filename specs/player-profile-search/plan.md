# Plan: Player profile search

## Approach

A Spring Boot REST API exposes two read-only endpoints (search by name, profile by id). The business logic depends on a `PlayerRepository` port with two adapters: a JSON adapter (active) and a JPA/database adapter (built, tested, **disabled by default**). A minimal Angular app calls the API and renders the search, the result list and the profile.

Main trade-offs:
- **Port + two adapters** rather than a JSON-only implementation: it is the only way to satisfy FR-12 (switch without touching the UI) and to prove it with a shared contract test.
- **JSON loaded once at startup, kept in memory**, with the failure remembered instead of thrown: matches FR-11 ("reflect the files at application start") and FR-13 (no crash).
- **Maven stays at the repository root** (where it already is) and Angular goes in `frontend/`; `CLAUDE.md` is updated to say so (see decision D1).

## Decisions (validated)

- **D1 - Layout.** Maven stays at the repository root (`pom.xml`, `mvnw`, `src/`), Angular goes in `frontend/`, and `CLAUDE.md` is updated accordingly (commands without `cd backend`).
- **D2 - Database engine.** Only the abstraction and H2 (test scope) are added. No production driver is chosen, since the database is not used.
- **D3 - Test data.** No fictive players are created. Tests use the real files from the `data` branch (`players.json`, `teams.json`), extracted into the local `data/` folder and never committed on the feature branch (see "Data"). Consequence: BackEnd tests need those files present locally; without them the data-dependent tests fail with an explicit message ("data files missing: extract them from the `data` branch") rather than being skipped silently. Angular component tests do not read `data/`: they use response stubs shaped like the API contract.
- **D4 - Existing Node prototype (`app/`, comparison skill), where present.** Untouched and out of scope.
- **D5 - Language of the data.** All data will be in English (team names included); the application displays team names as stored and hard-codes none.

## Components

### BackEnd (package `stats_foot.stats_foot.player`)

| Element | Role |
|---|---|
| `Player`, `PlayerStats`, `GoalkeeperStats`, `Team` (records) | Domain model. Statistics use wrapper types (`Integer`, `Double`) so `null` stays distinguishable from `0` (FR-10). |
| `PlayerRepository` (interface) | Port: `List<Player> findAll()`, `Optional<Player> findById(String id)`. Throws `PlayerDataUnavailableException` if the source cannot be read. |
| `JsonPlayerRepository` | Reads `players.json` + `teams.json` from `statsfoot.data-dir` (default `data`) at startup, joins team name/code, keeps the list in memory. A read error is stored and rethrown on use (no startup failure, FR-13). Active when `statsfoot.player-source=json` (default). |
| `JpaPlayerRepository`, `PlayerEntity`, `TeamEntity`, `PlayerJpaRepository` | Database adapter. Active only with the Spring profile `database` (`application-database.properties` sets `statsfoot.player-source=database` and re-enables the JPA/DataSource auto-configuration). Stores a normalised name column (`search_name`) kept for a future SQL-side search; for now `findAll()` returns every player and the filtering, sorting and cap stay in `PlayerService` (single source of logic, hence identical results). Not enabled anywhere in this feature. |
| `NameNormalizer` | `trim`, collapse whitespace, NFD + strip diacritics, `toLowerCase(Locale.ROOT)`. Shared by both adapters and the service (FR-2, FR-3). |
| `PlayerService` | `search(name)`: rejects blank (FR-3), filters on normalised name, sorts alphabetically, returns the first 50 plus `total` (FR-5). `getProfile(id)`: finds the player and computes the age with an injected `Clock` (FR-7). |
| `PlayerController` | `GET /api/players?name=` and `GET /api/players/{id}`. |
| `ApiExceptionHandler` | Maps errors to `{ "error": "<English message>" }`: blank query 400, unknown id 404, unreadable data 503 (FR-3, FR-6, FR-13, FR-14). |
| `ClockConfig` | `Clock` bean (`Clock.systemDefaultZone()`), replaced by a fixed clock in tests (AC-9b). |
| `application.properties` | `statsfoot.data-dir=data`, `statsfoot.player-source=json`, and `spring.autoconfigure.exclude` for the DataSource/Hibernate/JPA auto-configurations (see startup safety below). |
| `application-database.properties` | Profile `database`: `statsfoot.player-source=database` and an empty `spring.autoconfigure.exclude`. A datasource (`spring.datasource.*`) is provided at deployment. |

`pom.xml` changes: add the web starter (Spring Boot 4.x naming, `spring-boot-starter-webmvc`, to verify against 4.1.1), `spring-boot-starter-data-jpa` and `h2` (test scope), and the matching web test starter. **Startup safety:** JPA auto-configuration must not start without a datasource, so it is excluded by default and enabled only by the `database` profile (the property alone is not enough), otherwise the app would fail to boot or silently start an embedded H2 (violates FR-12/FR-13).

### FrontEnd (`frontend/`, Angular, standalone components)

| Element | Role |
|---|---|
| `PlayerApiService` | `search(name)` and `getProfile(id)` over `HttpClient`. |
| `PlayerSearchPage` | Name field + search button (FR-1), blank check message (FR-3). One result opens the profile directly (FR-4), several show the list (FR-5) with the "refine your search" message when `truncated` is true (total above 50), none shows "player not found" (FR-6), an API 503 shows the error message (FR-13). |
| `PlayerProfileCard` | Identity block (FR-7: team as "Name (CODE)", birth date, age), statistics block (FR-8), goalkeeper block shown only for goalkeepers (FR-9), `null` rendered as "not available" (FR-10). |
| `proxy.conf.json` | Dev proxy `/api` to `http://localhost:8080`, so no CORS configuration is needed. |

All labels and messages are in English (FR-14).

### Data

- No JSON file is added to `main` or to the branch (FR-11). The files were extracted with `git archive origin/data data/players.json data/teams.json | tar -x` and are excluded locally through `.git/info/exclude` (`/data/*.json`), so they cannot be staged by mistake. `data/README.md` stays the tracked one.
- Notes from the data (1248 players): pass accuracy is `null` for 171 players, goalkeeper statistics exist only for the 144 goalkeepers, names are unique today but the profile is looked up by `id`. Useful reference cases for tests: `P-0001` (goalkeeper, `null` pass accuracy), `P-0003` (non-null pass accuracy), `P-0005` (captain), `P-0020` (forward), `P-0004` "Kwame Ramírez" (accent), `sami suzuki` (exactly one match), `suz` (16 matches), `a` (1130 matches, above the 50 cap), `xyzxyz` (no match).

## Data model and contracts

`GET /api/players?name=suz` returns `200`:

```json
{ "total": 1, "truncated": false,
  "players": [ { "id": "P-0001", "name": "Sami Suzuki", "teamCode": "ESP", "teamName": "Spain", "position": "GK" } ] }
```

`truncated` is true when `total > 50` (only 50 items are returned). Blank `name`: `400 {"error": "Please enter a player name."}`.

`GET /api/players/P-0001` returns `200`:

```json
{ "id": "P-0001", "name": "Sami Suzuki", "teamCode": "ESP", "teamName": "Spain",
  "shirtNumber": 1, "position": "GK", "positionDetail": "GK",
  "birthDate": "2006-11-03", "age": 19, "club": "Club América", "heightCm": 190,
  "preferredFoot": "right", "captain": false,
  "stats": { "appearances": 0, "starts": 0, "minutesPlayed": 0, "goals": 0, "assists": 0, "ownGoals": 0,
             "shots": 0, "shotsOnTarget": 0, "yellowCards": 0, "redCards": 0,
             "foulsCommitted": 0, "foulsSuffered": 0, "passes": 0, "passAccuracy": null },
  "goalkeeperStats": { "saves": 0, "goalsConceded": 0, "cleanSheets": 0, "penaltiesSaved": 0 } }
```

`goalkeeperStats` is omitted for outfield players. Unknown id: `404 {"error": "Player not found."}`. Unreadable data: `503 {"error": "Player data is currently unavailable."}`.

No JSON file schema changes. Database schema (adapter only): `team(id, name, code)`, `player(id, name, search_name, team_id, ...)` with the same fields as the model.

## Spec traceability

| Requirement | Covered by |
|---|---|
| FR-1 | `PlayerSearchPage` form |
| FR-2 | `NameNormalizer`, `PlayerService.search`, SQL on `search_name` |
| FR-3 | `NameNormalizer`, blank check in `PlayerService` (400) and in `PlayerSearchPage` |
| FR-4 | `PlayerSearchPage` (opens profile when `total == 1`) |
| FR-5 | `PlayerService.search` (sort, cap 50, `total`/`truncated`), `PlayerSearchPage` list and message |
| FR-6 | `PlayerSearchPage` empty state, `404` for unknown id |
| FR-7 | `PlayerService.getProfile` (age via `Clock`), `PlayerProfileCard` identity block |
| FR-8 | Profile `stats`, `PlayerProfileCard` statistics block |
| FR-9 | `goalkeeperStats` optional in the API, conditional block in `PlayerProfileCard` |
| FR-10 | Wrapper types and `null` in the API, "not available" in `PlayerProfileCard` |
| FR-11 | `JsonPlayerRepository`, `statsfoot.data-dir`, no JSON in git, test fixtures |
| FR-12 | `PlayerRepository` port, `JpaPlayerRepository`, `statsfoot.player-source` default `json`, shared contract test |
| FR-13 | Stored read error in `JsonPlayerRepository`, `ApiExceptionHandler` (503), error state in `PlayerSearchPage` |
| FR-14 | English labels, messages and API errors |

## Tests (derived from the acceptance criteria)

- **Unit**: `NameNormalizerTest` (AC-2, AC-3), `PlayerServiceTest` with a fixed `Clock`, backed by `JsonPlayerRepository` on the real `data/` files (AC-4 to AC-9b, AC-12).
- **Contract**: abstract `PlayerRepositoryContractTest` run against `JsonPlayerRepository` and `JpaPlayerRepository` (H2 loaded from the same real players) to prove identical results (AC-13, AC-14); `JsonPlayerRepositoryTest` for the configurable folder and the missing/unreadable file, using temporary folders built by the test (AC-13b, AC-15).
- A shared test helper locates `data/` and fails with the explicit message of D3 when the files are missing.
- **API**: `@WebMvcTest` / `MockMvc` on both endpoints (AC-5 to AC-8, AC-11, AC-15), plus a context test checking that the default source is JSON and that the app starts without any datasource (AC-14).
- **Angular**: component tests for the search page and the profile card (AC-1, AC-4 to AC-8, AC-11, AC-12), using the default Angular test runner.

## Risks and alternatives rejected

- **Spring Boot 4.1.1 is recent**: starter names changed in 4.x (web, test starters). Verified at the first build; the build fails fast otherwise.
- **JPA on the classpath**: could start an embedded H2 or fail without a datasource. Mitigated by the default exclusion and a context test (see Components).
- **SQL vs in-memory search could diverge** (collation, accents). Mitigated by keeping the filtering in `PlayerService` for both adapters and by the shared contract test (the `search_name` column is prepared for a later SQL-side search).
- **BackEnd tests depend on local data files** (D3): a fresh clone or CI without `data/*.json` fails those tests until the files are extracted from the `data` branch. Accepted by the product owner; the explicit failure message and the README section make it visible.
- **Node and the Angular CLI are not verified** on this machine; checked in the first frontend task, with `npx` so no global install is required.
- *Rejected:* reading the JSON on every request (contradicts FR-11 "at application start" and is slower); a single JSON-only service with a later rewrite (breaks FR-12); serving Angular from Spring (couples the build for no benefit now); reusing `app/index.html` and `lib.mjs` (French texts, different stack, hard-coded 2026-06-11 age date).
