# Tasks: Player profile search

Conventions: tests are written first (or with the code) and seen failing before the implementation. BackEnd tests use the real `data/players.json` and `teams.json` (local, never committed, see plan D3). Test method names are in English like the rest of the product. Reference data: `P-0001` (goalkeeper, birth 2006-11-03, `passAccuracy` null), `P-0003` (non-null pass accuracy), `P-0005` (captain), `P-0020` (forward), `P-0004` "Kwame Ramírez"; queries `sami suzuki` (1), `suz` (16), `ou` (exactly 50), `id` (exactly 51), `a` (1130), `xyzxyz` (0). All values asserted in tests are read back from the JSON by the test, not copied by hand.

Common check after every BackEnd task: `./mvnw test` is green. After every FrontEnd task: `npm test` (in `frontend/`) is green.

## BackEnd

- [x] T1 (FR-11, FR-12) Dependencies, configuration and test data helper
  - Files: `pom.xml`, `src/main/resources/application.properties`, `src/main/java/stats_foot/stats_foot/player/ClockConfig.java`, `src/test/java/stats_foot/stats_foot/player/TestDataFiles.java`
  - Work: add the Spring Boot 4.x web starter and its test starter, `spring-boot-starter-data-jpa`, H2 (test scope); exclude JPA/DataSource auto-configuration by default; add `statsfoot.data-dir=data` and `statsfoot.player-source=json`; add the `Clock` bean; add `TestDataFiles` (locates `data/`, fails with "data files missing: extract them from the `data` branch" when absent).
  - Done when: `./mvnw test` is green with the existing `contextLoads` (no datasource needed); a `TestDataFilesTest` on an empty temp folder shows the explicit message.

- [x] T2 (FR-2, FR-3, AC-2, AC-3) `NameNormalizer`
  - Files: `player/NameNormalizer.java`, `NameNormalizerTest.java`
  - Work: trim, collapse all whitespace (including non-breaking space and tabs), NFD + strip diacritics, `toLowerCase(Locale.ROOT)`; blank and `null` give an empty result.
  - Done when: `NameNormalizerTest` passes: `SUZUKI` gives `suzuki`, `sámi` gives `sami`, `Ramírez` gives `ramirez`, `  sami   suzuki  ` gives `sami suzuki`, `""`, `"   "` and `null` give empty, idempotent.

- [x] T3 (FR-7, FR-8, FR-9, FR-10) Domain model
  - Files: `player/Player.java`, `PlayerStats.java`, `GoalkeeperStats.java`, `Team.java`
  - Work: records with wrapper types so that `null` stays distinct from `0`; `GoalkeeperStats` optional on `Player`.
  - Done when: the module compiles; covered by T4 tests (JSON mapping keeps `passAccuracy` null for `P-0001` and `0` for a zero stat).

- [x] T4 (FR-11, FR-13, AC-13, AC-13b, AC-15) `PlayerRepository` port and `JsonPlayerRepository`
  - Files: `player/PlayerRepository.java`, `PlayerDataUnavailableException.java`, `JsonPlayerRepository.java`, `JsonPlayerRepositoryTest.java`
  - Work: read `players.json` and `teams.json` once at startup from `statsfoot.data-dir`, join team name and code, keep in memory; a read error is stored and rethrown as `PlayerDataUnavailableException` by `findAll`/`findById`, never at construction.
  - Done when: `JsonPlayerRepositoryTest` passes: values equal an independent JSON parse for `P-0001` and `P-0003`; reads from a configured temp folder holding copies of the real files; empty or missing folder, missing `players.json`, missing `teams.json` and non-JSON `players.json` all give the exception on use and none at startup; the same error on repeated calls; a file changed after startup is not re-read.

- [x] T5 (FR-12, AC-14) Repository contract test (JSON run)
  - Files: `player/PlayerRepositoryContractTest.java` (abstract), `JsonPlayerRepositoryContractTest.java`
  - Work: abstract contract: `findAll` returns 1248 players; `findById` returns identical records for `P-0001`, `P-0003`, `P-0004`, `P-0005`, `P-0020`; unknown id gives empty; `passAccuracy` null preserved.
  - Done when: the JSON run passes (the JPA run is added in T9).

- [x] T6 (FR-2, FR-3, FR-4, FR-5, FR-6, AC-2 to AC-8) `PlayerService.search`
  - Files: `player/PlayerService.java`, `PlayerSearchResult.java` (summary + `total` + `truncated`), `PlayerServiceTest.java`
  - Work: reject null/blank with a business exception; filter on normalised name; sort alphabetically on the normalised name; cap at 50; `total` is the full count; `truncated` is true only when `total > 50`.
  - Done when: `PlayerServiceTest` passes: `suz`, `SUZUKI`, `sámi` find Sami Suzuki; `  sami   suzuki  ` equals `sami suzuki` (total 1); `suz` gives 16 sorted; `ou` gives 50 and `truncated=false`; `id` gives 50 of 51 and `truncated=true`; `a` gives 50 of 1130 and `truncated=true`; `xyzxyz` gives an empty result without exception; `ramirez` finds `P-0004`; null, `""`, `"   "` raise the business exception.

- [x] T7 (FR-7 to FR-10, AC-9, AC-9b, AC-10 to AC-12) `PlayerService.getProfile`
  - Files: `player/PlayerService.java`, `PlayerProfile.java`, `PlayerServiceProfileTest.java`
  - Work: build the profile with the age computed from the injected `Clock`; omit goalkeeper statistics for outfield players; unknown id raises a "player not found" exception.
  - Done when: with a fixed clock, `P-0001` has age 19 on 2026-10-20 and 2026-11-02, and 20 on 2026-11-03; identity and the 14 statistics equal the JSON for `P-0003` and `P-0005`; `passAccuracy` is null for `P-0001` and non-null for `P-0003`; a zero statistic stays `0`; goalkeeper statistics present for `P-0001` and absent for `P-0020`; `P-9999` raises the not-found exception.

- [x] T8 (FR-3, FR-5, FR-6, FR-13, FR-14, AC-4 to AC-8, AC-15, AC-16) REST controller and error handling
  - Files: `player/PlayerController.java`, `ApiExceptionHandler.java`, `PlayerControllerTest.java`, `ApiExceptionHandlerTest.java`
  - Work: `GET /api/players?name=` and `GET /api/players/{id}` following the contract in `plan.md`; errors as `{"error": "..."}`: blank or missing `name` gives 400 "Please enter a player name.", unknown id gives 404 "Player not found.", `PlayerDataUnavailableException` gives 503 "Player data is currently unavailable." without leaking paths or stack traces.
  - Done when: MockMvc tests pass: `sami suzuki`, `suz`, `ou`, `id`, `a`, `xyzxyz`, `name=sámi`, blank and missing name, `P-0001` (fixed clock, `passAccuracy` null, `goalkeeperStats` present), `P-0003`, `P-0005`, `P-0020` (`goalkeeperStats` key absent), `P-9999`; the 503 body contains no file path; the three error messages are in English.

- [x] T9 (FR-12, AC-13b, AC-14) Database adapter, disabled by default
  - Files: `player/JpaPlayerRepository.java`, `PlayerEntity.java`, `TeamEntity.java`, `PlayerJpaRepository.java`, `JpaConfig.java`, `JpaPlayerRepositoryContractTest.java`, `PlayerSourceConfigTest.java`, `src/main/resources/application-database.properties`
  - Work: JPA adapter with a normalised name column (`search_name`), active only with the `database` profile (which sets `statsfoot.player-source=database`); JPA/DataSource auto-configuration enabled only in that case.
  - Done when: the contract test passes on H2 loaded from the real players, with identical records and identical search results (same ids, same order) for `suz`, `sami suzuki`, `ou`, `id`, `a`, `xyzxyz`, `ramirez`; `PlayerSourceConfigTest` shows the default context has a `JsonPlayerRepository`, no `JpaPlayerRepository` and no `DataSource`, reads a configured `data-dir`, and that the `database` profile swaps the adapter with `PlayerService` and the controller unchanged.

- [x] T10 (FR-13, AC-15) Startup without readable data
  - Files: `PlayerDataUnavailableIntegrationTest.java`
  - Done when: with `statsfoot.data-dir` on an empty or missing temp folder, and with a non-JSON `players.json`, the context starts and the search answers 503 with the English message, twice in a row.

## FrontEnd

- [x] T11 (FR-1, FR-14) Angular project
  - Files: `frontend/` (new Angular standalone project with its default test runner), `frontend/proxy.conf.json`
  - Work: first check that Node and npm are available; create the project (via `npx`, no global install); `npm start` uses the proxy `/api` to `http://localhost:8080`.
  - Done when: `npm install`, `npm test` (default spec) and `npm start` work; the proxy file is in place.

- [x] T12 (FR-1, FR-5, FR-7) `PlayerApiService`
  - Files: `frontend/src/app/player/player-api.service.ts` and its spec
  - Done when: spec passes with `HttpTestingController`: `search` calls `GET /api/players` with the encoded `name` (`sámi & co`), `getProfile` calls `GET /api/players/P-0001`, and 400, 404, 503 errors are propagated.

- [x] T13 (FR-7 to FR-10, AC-9 to AC-12, AC-16) `PlayerProfileCard`
  - Files: `frontend/src/app/player/player-profile-card/` and its spec
  - Work: identity block ("Name (CODE)", birth date, age, captain), statistics block, goalkeeper block only when present, `null` shown as "not available" and `0` shown as `0`; English labels.
  - Done when: spec passes with API-shaped stubs: all identity fields and the 14 statistics are shown; "not available" for a null `passAccuracy`; `0` for a zero statistic; goalkeeper block present for a goalkeeper and absent from the DOM for an outfield player.

- [x] T14 (FR-1, FR-3 to FR-6, FR-13, AC-1, AC-4 to AC-8, AC-15) `PlayerSearchPage` and application shell
  - Files: `frontend/src/app/player/player-search-page/` and its spec; `app.ts`/routes updated
  - Work: name field and search button; blank check without HTTP call; one result opens the profile; several results show name, team and position, a click opens the profile, and a "refine your search" message when `truncated`; no result shows "player not found"; API error message shown (503 and 400); previous results cleared on each search.
  - Done when: spec passes with API stubs: field and button visible; blank and spaces-only input shows "please enter a player name" with no HTTP call; `total 1` opens the profile directly; 16 rows then profile on click; 50 rows plus the refine message when `truncated` and no message when not; empty result shows "player not found"; a 503 shows "Player data is currently unavailable." and the page stays usable.

## Project, documentation and final checks

- [x] T15 (D1, FR-11) Project documentation
  - Files: `CLAUDE.md`, `README.md`
  - Work: `CLAUDE.md` commands: Maven at the repository root (no `cd backend`), `frontend/` for Angular, and a short note on the local `data/*.json` files; README: how to extract the data from the `data` branch (`git archive origin/data data/players.json data/teams.json | tar -x`), the configuration properties `statsfoot.data-dir` and `statsfoot.player-source`, the two endpoints, how to run backend and frontend.
  - Done when: the documented commands run as written on a clean machine (checked by the `relecteur-doc` agent).

- [x] T16 (FR-11, AC-13b) No data file committed
  - Done when: `git ls-files data` lists only `data/README.md` and `git status --short` never shows `data/*.json` staged or untracked (checked by a review step, not by a git-dependent test).

- [x] T17 (all FR) End-to-end check with the real data
  - Done when: with the real `data/` files, `./mvnw spring-boot:run` plus `npm start` let a user search `sami suzuki`, `suz`, `a`, `xyzxyz`, an empty value, and open `P-0001` and `P-0020`, with results matching the acceptance criteria; stopping the backend shows the error message. The spec compliance table is then produced (SDD step 5).
