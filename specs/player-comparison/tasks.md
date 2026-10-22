# Tasks: Player comparison

Paths: backend `src/main/java/stats_foot/stats_foot/player/` (`B/`), tests `src/test/java/stats_foot/stats_foot/player/` (`BT/`), frontend `frontend/src/app/` (`F/`). Tests are written before or with the code.

## Backend

- [x] T1 (FR-4, FR-5, FR-6, FR-7) Add comparison DTOs and enums
  - Files: `B/model/PlayerRole.java`, `B/dto/PlayerComparison.java` (with `ComparedPlayer`, `ComparisonLine`, `ComparisonSummary`), `B/dto/Leader.java`
  - Done when: `./mvnw test` compiles; records match the contract in `plan.md`.

- [x] T2 (FR-2, FR-3) Add comparison exceptions
  - Files: `B/exception/SamePlayerComparisonException.java`, `B/exception/ComparisonPlayerNotFoundException.java` (extends `PlayerNotFoundException`)
  - Done when: both compile and carry the messages "The two players must be different." and "Player A/B not found.".

- [x] T3 (FR-10, AC-11) Implement the verdict builder
  - Files: `B/service/ComparisonVerdict.java`, `BT/ComparisonVerdictTest.java`
  - Done when: `ComparisonVerdictTest` passes: `aLeads_namesAAndCounts`, `equalCounts_saysLevel`, `atMostThreeMainDifferences` (sorted by gap then table order, ties / no-leader / unavailable / 0-vs-0 never cited), `noCandidate_omitsSentence2`, `differentRoles_appendsWarning`, `sameInput_sameVerdict`.

- [x] T4 (FR-1, FR-2, FR-3, FR-4, FR-5, FR-6, FR-7, FR-8, FR-9; AC-1 to AC-10) Implement `PlayerComparisonService`
  - Files: `B/service/PlayerComparisonService.java`, `BT/PlayerComparisonServiceTest.java`
  - Done when: `PlayerComparisonServiceTest` passes with hand-built `Player` objects: `compare_twoExistingPlayers_returnsReport`, `compare_sameId_throwsSamePlayer` (repository never called), `compare_unknownA` / `_unknownB` / `_bothUnknown` (names A), `compare_roles_derivedFromGoalkeeperMatches`, `compare_returnsNineteenLinesInOrder`, `compare_doubleDifferenceRounded`, `goals_higherLeads`, `yellowCards_lowerLeads` (parameterised over the 7 lower-is-better keys), `equalValues_tie`, `noLeaderStats_neverDesignateLeader` (matches played, matches started, minutes, distance), `nullValue_lineNotAvailable`, `summary_countsMatchLines`, `roleWarning_whenRolesDiffer`, `roleWarning_nullWhenSameRole`.

- [x] T5 (FR-11, FR-3, FR-2, FR-14; AC-2, AC-3, AC-16) Extend the error handler
  - Files: `B/controller/ApiExceptionHandler.java`, `BT/ApiExceptionHandlerTest.java`
  - Done when: `ApiExceptionHandlerTest` passes with new cases (same player 400, named 404, missing `playerB` 400 "Both playerA and playerB are required.", `playerA=abc` 400 "Player ids must be integers.") and all existing cases unchanged (`@MockitoBean PlayerComparisonService` added to its context).

- [x] T6 (FR-11; AC-1, AC-2, AC-3, AC-4, AC-8, AC-12, AC-13, AC-16) Expose `GET /api/players/compare`
  - Files: `B/controller/PlayerController.java`, `BT/PlayerComparisonControllerTest.java`
  - Done when: `PlayerComparisonControllerTest` passes on the real data: `compare_validIds_returns200` (1 and 2), `compare_sameId_returns400`, `compare_unknownA_returns404` / `_unknownB`, `compare_sameUnknownId_returns400`, `compare_gkVsOutfield_roles`, `compare_nullRating_player316`, `compare_jsonShape`, `compare_isReadOnly` (POST/PUT give 405), `service_sameReportAsHttp`, `errors_areEnglish`, a 503 case when data is unavailable; `/api/players/1` still returns the profile and `/compare` is not routed to `/{id}`.

- [x] T7 (non-regression) Run the full backend suite
  - Done when: `./mvnw test` is green, including `PlayerControllerTest`, `PlayerServiceTest`, `PlayerServiceProfileTest`, the repository contract tests and `PlayerDataUnavailableIntegrationTest`.

## Frontend

- [x] T8 (FR-12) Add types and `compare()` to the API service
  - Files: `F/player/player.models.ts`, `F/player/player-api.service.ts`, `F/player/player-api.service.spec.ts`
  - Done when: `compare_sendsGetWithParams` passes (GET `/api/players/compare`, params `playerA=1`, `playerB=2`).

- [x] T9 (FR-12) Extract `PlayerPicker` from `PlayerSearchPage`
  - Files: `F/player/player-picker/*`, `F/player/player-search-page/*`
  - Done when: `player-picker` spec passes (`emitsPlayerSelected`: single result auto-selected, several results emit on click; `refineMessage_and_errors`) and the existing `player-search-page` specs pass with their `data-testid` values unchanged.

- [x] T10 (FR-12, FR-13, FR-14; AC-14, AC-15, AC-16) Build `PlayerComparisonPage` (selection UI superseded by T16)
  - Files: `F/player/player-comparison-page/*` (ts, html, css, spec)
  - Done when: `player-comparison-page` spec passes: `compare_disabledUntilBothChosen`, `displaysReport` (identities, summary, verdict, warning only when present), `showsApiError` (API message then generic error), `nullValue_showsNotAvailable`, `bar_leaderA` / `_leaderB` / `_tie` / `_noLeader` / `_unavailable`, `bar_width_relativeGap` (5 vs 3 → 40%), `bar_bothZero_noDivisionByZero`, `bar_lowerIsBetter` (4 vs 2, leader B, 50%), `texts_areEnglish`.

- [x] T11 (FR-12, FR-14) Add route and navigation
  - Files: `F/app.routes.ts`, `F/app.html`, `F/app.spec.ts`
  - Done when: `/compare` shows the comparison page with title "Player comparison", the nav shows "Search" and "Compare" in English, and `app.spec.ts` passes.

- [x] T12 (non-regression) Run the full frontend suite
  - Done when: `cd frontend && npm test` is green.

## Closing

- [x] T13 Conformity check and documentation
  - Done when: the `relecteur` conformity table shows every FR/AC as covered, and the documentation is updated for the new endpoint and page. No commit (explicit user instruction).

## Amendment: name-based input (FR-12 reworked, FR-12b, FR-15)

- [x] T14 (FR-15, AC-21) Add the by-name exceptions
  - Files: `B/exception/BlankComparisonNameException.java`, `B/exception/AmbiguousPlayerNameException.java`
  - Done when: both compile; messages as in `plan.md`.

- [x] T15 (FR-15, AC-20, AC-21) Implement `PlayerComparisonService#compareByName`
  - Files: `B/service/PlayerComparisonService.java`, `BT/service/PlayerComparisonServiceTest.java`
  - Done when: existing tests still pass with `PlayerService` mocked in the constructor, and new tests pass: `compareByName_resolvesNamesWithCaseAndAccents` (report equals the by-id report), `compareByName_blankName` (A or B), `compareByName_unknownName`, `compareByName_severalMatches_listsAtMostTenCandidates` (with "and N more"), `compareByName_sameResolvedPlayer_throwsSamePlayer`, `compareByName_resolvesAThenB` (A error wins when both are wrong); plus one `@SpringBootTest` on the real data (two known names give the report of the corresponding ids).

- [x] T16 (FR-12, FR-12b, FR-14; AC-14, AC-16, AC-17, AC-18, AC-19) Rework `PlayerComparisonPage` to two text fields and a single button
  - Files: `F/player/player-comparison-page/*`
  - Done when: the `player-comparison-page` spec passes: `twoFieldsAndOneButton_noPerFieldSearchButton`, `compare_resolvesBothNamesThenShowsReport`, `blankField_showsErrorAndSendsNoRequest`, `unknownName_showsPlayerNotFoundForThatField`, `severalMatches_listsCandidatesUnderField`, `clickingCandidate_thenCompareAgain_showsReport`, `truncatedCandidates_showsRefineMessage`, `editingField_clearsChosenCandidatesErrorAndReport`, `searchError_showsApiOrGenericMessage`, `samePlayerTwice_showsApiError`, plus the existing report, bar and English-text specs with unchanged behaviour; the `PlayerSearchPage` and `PlayerPicker` specs unchanged and green.

- [x] T17 Non-regression, conformity and documentation
  - Done when: `./mvnw test -Dspring.ai.openai.api-key=dummy` and `cd frontend && npm test` are green, the `code-reviewer` conformity table covers FR-12, FR-12b, FR-15 and AC-14, AC-17 to AC-21, and `README.md` documents the by-name service method and the new page behaviour. No commit (explicit user instruction).

## Amendment: exact match first (FR-12b, FR-15)

- [x] T18 (FR-15, AC-23) Exact match first in `compareByName`
  - Files: `B/service/PlayerComparisonService.java`, `BT/service/PlayerComparisonServiceTest.java`
  - Done when: new tests pass (`compareByName_exactNameAmongSeveralMatches_usesExactPlayer`, `compareByName_exactMatchIgnoresCaseAccentsAndSpaces`, `compareByName_severalExactNames_stillAmbiguous`, `compareByName_noExactName_stillAmbiguous`) and all existing by-name tests stay green.

- [x] T19 (FR-12b, AC-22) Exact match first on the page
  - Files: `F/player/player-comparison-page/*`
  - Done when: new specs pass (`exactNameAmongSeveralMatches_usesExactPlayerWithoutList`, `exactMatchIgnoresCaseAccentsAndSpaces`, `severalExactNames_showsCandidateList`, `noExactName_showsCandidateList`) and the existing page specs stay green.

- [x] T20 Non-regression and documentation
  - Done when: `./mvnw test -Dspring.ai.openai.api-key=dummy` and `cd frontend && npm test` are green and `README.md` mentions the exact-match-first rule. No commit.
