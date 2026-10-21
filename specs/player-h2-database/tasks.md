# Tasks: Player data from an H2 database

Conventions: tests are written first (or with the code) and seen failing before the implementation. All tests use the real `data/donneesStats.json` (versioned) and **in-memory** H2 databases; they never write `data/h2/`. Expected values are read back from the file by the test, not copied by hand. The test cases come from the "Tests" section of `plan.md`.

Reference data (from the file): 1 competition, 48 teams, 1039 players, 104 matches, 16 stadiums, 42 referees, 3288 line-ups, 1000 substitutions, 308 goals, 282 bookings, 40 penalty kicks, 3288 player statistic rows, 208 team statistic rows. Players: id `1` "Raul RANGEL" (Mexico, `MEX`, 5 goalkeeper matches, 0 goals, 438 minutes), id `2` "Cesar MONTES" (3 captain matches), id `316` "Haji WRIGHT" (USA, 2 matches played, 0 minutes, null weighted rating). Queries: `rangel` (1), `so` (exactly 50 players), `te` (exactly 51), `a` (913), `xyzxyz` (0).

Order matters: T1 to T4 only add the new `database` package and keep the old JSON code working, so `./mvnw test` stays green; T5 is the switch.

Common check after every BackEnd task: `./mvnw test` is green. After every FrontEnd task: `npm test` (in `frontend/`) is green.

## BackEnd - database

- [x] T1 (FR-1, AC-1) H2 schema script
  - Files: `src/main/resources/db/schema.sql`, `src/test/java/stats_foot/stats_foot/database/SchemaScriptTest.java`
  - Work: port parts 1, 2 and 4 of `data/donneesStats.sql` to H2 (tables `competition`, `team`, `stadium`, `referee`, `player`, `match`, `match_lineup`, `substitution`, `goal`, `booking`, `penalty_kick`, `player_match_rating`, `team_match_rating`, `player_match_stats`, `team_match_stats`, indexes) with the deviations D1 of `plan.md` (`team.fifa_code`, `player_match_stats.is_goalkeeper`, nullable `referee.country_code`, no partial index, H2 syntax and keyword handling); add the view `v_player_tournament_stats` rewritten without `LATERAL` (D2) with `goalkeeper_matches` and `captain_matches`. Everything idempotent (`IF NOT EXISTS`, `CREATE OR REPLACE`). Settle the connection URL options (`MODE=PostgreSQL`, `NON_KEYWORDS`) and record them in a comment.
  - Done when: `SchemaScriptTest` (plain in-memory `DataSource`, no Spring) runs the script twice without error and finds every table with the columns of the SQL schema plus the three deviations, and the view with its columns.

- [x] T2 (FR-2, AC-2, AC-3, AC-7) Importer of `donneesStats.json`
  - Files: `src/main/java/stats_foot/stats_foot/database/DataFileImporter.java`, `DataFileImporterTest.java`
  - Work: read the file with Jackson 3 (`tools.jackson`) and insert with batched `JdbcTemplate` calls and the identifiers of the file: competition, teams (name, FIFA code), players, stadiums and referees (deduplicated), matches (period scores, penalty score, kickoff with offset), line-ups, substitutions, goals, bookings, penalty kicks, player and team match ratings (`algorithm_version = 'v1'`), player and team match statistics (with `is_goalkeeper`); restart the identity columns afterwards. The file path is a constructor argument.
  - Done when: `DataFileImporterTest` passes: every table has the row count of the file (reference counts above); for players `1`, `2` and `316` the match rows equal the file and the minutes sum equals the `tournament` block; exactly one man of the match per match; identifiers are those of the file; an inserted row after the import gets a new identifier without collision; a path to another copy of the file in a `@TempDir` works.

- [x] T3 (FR-7, AC-8) Tournament view equals the file
  - Files: `PlayerTournamentStatsViewTest.java`, `schema.sql` (fixes if needed)
  - Done when: the test imports the file into a fresh in-memory database and compares, for **all 1039 players**, every column of `v_player_tournament_stats` with the `tournament` block of the file (decimals compared with a tolerance of 0.01, nulls compared as nulls, `rating_weighted` null for players without minutes), and compares `goalkeeper_matches` and `captain_matches` with the file.

- [x] T4 (FR-3 to FR-6, FR-16, AC-4 to AC-7, AC-17) Database initializer and status
  - Files: `database/DatabaseInitializer.java`, `database/DatabaseStatus.java`, `DatabaseInitializerTest.java`
  - Work: `DatabaseInitializer` (a `SmartInitializingSingleton`) runs `schema.sql`, then, when `SELECT COUNT(*) FROM player` is `0`, runs the importer in a single `TransactionTemplate`; every exception is caught, logged without leaking paths into API messages, and stored in `DatabaseStatus`. It takes the data file path and the `DataSource` as inputs. Not wired into Spring yet beyond being a bean (activated for real in T5).
  - Done when: `DatabaseInitializerTest` passes: an empty database gets imported; a second run on the filled database imports nothing and duplicates nothing; a copy of the file truncated halfway in a `@TempDir` leaves **zero rows in every table** and a failure in `DatabaseStatus`, and a following run with the complete file imports everything; a missing file and an unreachable database (invalid URL) store a failure and do not throw.

## BackEnd - switch to the database

- [x] T5 (FR-8 to FR-17, AC-9 to AC-18) Read the players from the database
  - Files:
    - build and configuration: `pom.xml` (`h2` scope `runtime`), `src/main/resources/application.properties` (remove `statsfoot.player-source`, `statsfoot.data-dir`, `spring.autoconfigure.exclude`; add `statsfoot.database-file`, `statsfoot.data-file`, the H2 file datasource URL, `ddl-auto=none`, `open-in-view=false`, Hibernate no-metadata-access settings), `src/test/resources/application.properties` (in-memory URL, real data file), `.gitignore` (`data/h2/`);
    - to delete: `repository/JsonPlayerRepository.java`, `config/ClockConfig.java`, `config/JpaConfig.java`, `model/GoalkeeperStats.java`, `src/main/resources/application-database.properties`;
    - to rewrite or adapt: `model/Player`, `model/Team`, `model/PlayerStats`, `entity/PlayerEntity`, `entity/TeamEntity`, new `entity/PlayerTournamentStatsEntity`, `repository/PlayerRepository`, `repository/PlayerJpaRepository`, `repository/JpaPlayerRepository`, `dto/PlayerProfile`, `dto/PlayerSummary`, `service/PlayerService`, `controller/PlayerController`, `controller/ApiExceptionHandler` (non-numeric id gives 404), and all affected tests.
  - Work: follow the "Components" section of `plan.md`. Remove the `Clock` and the age. `JpaPlayerRepository` loads players with team and totals in one fetch-join query and checks `DatabaseStatus` before reading. Tests: delete `JsonPlayerRepositoryTest`, `JsonPlayerRepositoryContractTest`, `TestDataFiles`, `TestDataFilesTest`, `PlayerSourceConfigTest`; rewrite `PlayerRepositoryContractTest`/`JpaPlayerRepositoryContractTest`, `PlayerServiceTest`, `PlayerServiceProfileTest`, `PlayerControllerTest`, `PlayerDataUnavailableIntegrationTest`, `StatsFootApplicationTests`; keep `NameNormalizerTest` and `ApiExceptionHandlerTest` (adapted to the new signatures).
  - Done when: `./mvnw test` is green and covers:
    - the repository returns 1039 players, with identity, team and totals of ids `1`, `2`, `316` equal to the file; an unknown id gives empty;
    - search `rangel`, `RANGEL`, `  raul   rangel  ` finds id `1`; `so` gives 50 and not truncated, `te` gives 50 of 51 and truncated, `a` gives 50 of 913 sorted, `xyzxyz` gives none, blank input is rejected;
    - the profile has no removed field and no `goalkeeperStats`, `0` stays `0` (id `1` goals) and null stays null (id `316` weighted rating);
    - API: search items without `position`, `/api/players/1`, `/api/players/999999` and `/api/players/abc` give 200, 404, 404 with `{"error":"Player not found."}`; blank or missing name gives 400;
    - the application starts with an in-memory database and imports; the application starts with an unreachable database and every endpoint answers 503 `{"error":"Player data is currently unavailable."}` without any path in the body;
    - a source-scan test shows that no class under `src/main` refers to `players.json` or `teams.json`.

- [x] T6 (FR-3, AC-4, AC-5) Real startup with the local file
  - Files: none new (verification), fixes if needed
  - Work: start the real application (`./mvnw spring-boot:run`), check the file `data/h2/statsfoot*` is created, measure the first-start import, query `/api/players?name=rangel` and `/api/players/1`, stop and restart (no re-import, same data), then delete the file and restart (re-import).
  - Done when: the four checks behave as written, `git status` does not list `data/h2/`, and the import duration is noted in the report.

## FrontEnd

- [x] T7 (FR-15, FR-17) Models and API service
  - Files: `frontend/src/app/player/player.models.ts`, `player-api.service.ts`, `player-api.service.spec.ts`
  - Done when: `npm test` passes with the new contract: `id: number`; `PlayerSummary` without `position`; `PlayerStats` with the 19 totals (`number | null`); `PlayerProfile` without the removed fields and without `goalkeeperStats`, with `goalkeeperMatches` and `captainMatches`; `getProfile(1)` calls `GET /api/players/1`; 400, 404, 503 are propagated.

- [x] T8 (FR-11 to FR-14, AC-13 to AC-15) `PlayerProfileCard`
  - Files: `frontend/src/app/player/player-profile-card/` (`.ts`, `.html`, `.css`, `.spec.ts`)
  - Work: identity block (name, "Name (CODE)", goalkeeper matches, captain matches); statistics block with the 19 totals and English labels; goalkeeper block and the removed fields deleted; `0` shown as `0`, `null` as "not available".
  - Done when: the spec passes with stubs shaped like the new contract: all identity fields and the 19 totals shown; "not available" for a null `ratingWeighted`; `0` shown for zero goals; none of the removed items (shirt number, position, birth date, age, club, height, foot, assists, shots, passes, goalkeeper statistics) present in the DOM.

- [x] T9 (FR-9, FR-10, FR-16, AC-12, AC-17) `PlayerSearchPage`
  - Files: `frontend/src/app/player/player-search-page/` (`.ts`, `.html`, `.spec.ts`)
  - Work: result list shows name and "team (CODE)" only; ids are numbers; behaviour and messages unchanged.
  - Done when: `npm test` and `npm run build` pass; the spec covers one result opening the profile directly, a list without position then profile on click, 50 rows plus the refine message when truncated, "Player not found.", the 503 message and the 400 message.

## Documentation and final checks

- [x] T10 (D3, AC-4) Documentation and amendment notes
  - Files: `README.md`, `CLAUDE.md`, `data/README.md`, `specs/player-profile-search/{spec,plan,tasks}.md`
  - Work: README (data files now versioned, first-start import and `data/h2/`, configuration table with `statsfoot.database-file` and `statsfoot.data-file`, the new API contract, how to reload the data by deleting the file); `CLAUDE.md` (architecture "JSON then H2", traps: `data/h2/` is ignored, delete it to reload, the old extraction instructions removed); `data/README.md` gets a section on `donneesStats.json`, `donneesStats.sql` and `data/h2/`, the old `players.json`/`teams.json` content is marked as no longer used; the three old spec files get the note "amended by `specs/player-h2-database/`".
  - Done when: the documented commands run as written (checked by the `relecteur-doc` agent).

- [x] T11 (all FR) End-to-end check and conformity
  - Done when: with the real backend and frontend, a user can search `rangel`, `a`, `xyzxyz`, an empty value, and open id `1` and id `316`, with results matching the acceptance criteria; stopping the backend shows the error message; the `relecteur` agent confirms the conformity table (every FR implemented, every AC covered) against `spec.md`.
