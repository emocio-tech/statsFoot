# Plan: Player comparison

## Approach

Add a pure Spring bean `PlayerComparisonService` (no HTTP dependency, business exceptions only) that reads the two players through the existing `PlayerRepository` and builds a `PlayerComparison` report. A new endpoint in `PlayerController` exposes it. The service also gets a by-name entry point `compareByName` that resolves each name with the existing `PlayerService.search` (FR-15). On the frontend, a new `compare` page has two text fields and one "Compare" button: it resolves the names with the existing search endpoint, handles not-found / several matches per field, then calls the comparison endpoint by ids and renders the report with plain HTML/CSS bars (no chart library).

Main trade-offs:
- **Separate service** rather than a method in `PlayerService`: keeps the search/profile class readable and gives the maintainer a single, clearly named bean to wrap in a `@Tool` later.
- **Deterministic verdict** from a pure function, no LLM (spec out of scope).
- **HTML/CSS bars** rather than a charting dependency: no new dependency, testable in jsdom.
- Existing error contract of `/api/players/{id}` (`{"error":"Player not found."}`) stays untouched; comparison errors use new exceptions and handlers.

## Components

### Backend (`stats_foot.stats_foot.player`)

- `model/PlayerRole` (enum): `GOALKEEPER`, `OUTFIELD_PLAYER`. Goalkeeper iff `goalkeeperMatches > 0`.
- `dto/PlayerComparison` (record): `ComparedPlayer playerA`, `ComparedPlayer playerB`, `List<ComparisonLine> lines`, `ComparisonSummary summary`, `String roleWarning` (null when roles are equal), `String verdict`.
  - `ComparedPlayer(int id, String name, String teamName, String teamCode, PlayerRole role)`
  - `ComparisonLine(String key, String label, Number valueA, Number valueB, Number difference, Leader leader, boolean available)`
  - `ComparisonSummary(int leadsA, int leadsB, int ties)`
  - `Leader` (enum): `A`, `B`, `TIE`; `null` when the line has no leader.
- `service/PlayerComparisonService`: `PlayerComparison compare(int idA, int idB)`.
  - Holds an ordered table of the 19 statistics (FR-5 order = `PlayerStats` field order): `key` (camelCase contract key), English `label` (same wording as the profile card, e.g. "Distance (km)", "Rating (weighted by minutes)"), a `Direction` (`HIGHER_BETTER`, `LOWER_BETTER`, `NO_LEADER`) and a `PlayerStats -> Number` extractor.
    - `LOWER_BETTER`: ownGoals, yellowCards, redCards, foulsCommitted, freeKicksConceded, duelsLost, offsides.
    - `NO_LEADER`: matchesPlayed, matchesStarted, minutesPlayed, distanceKm.
    - `HIGHER_BETTER`: all others.
  - Order of checks: same id (400) first, then player A lookup, then player B lookup.
  - Difference = A − B, rounded to 2 decimals (HALF_UP) for `Double` statistics, exact for integers. A line is `available = false` (values kept, `difference` and `leader` null) when either value is null.
  - Summary counts only lines with a leader (`A`, `B`, `TIE`).
  - Role warning text: `"These players do not play the same role (goalkeeper vs outfield player): the comparison is not very relevant."` (roles named in the order A / B).
  - Verdict built by a package-private pure function `ComparisonVerdict.build(...)`:
    - Relative gap of a line = `|A − B| / max(|A|, |B|)`; only lines with a leader of `A` or `B` are candidates (ties, no-leader and unavailable lines are excluded, so equal-zero lines never appear).
    - Top gaps: sort by gap descending, then by table order, keep at most 3.
    - Sentence 1: `"<A> leads on N statistics to M for <B> (T tied)."`, or `"<A> and <B> are level: N statistics each (T tied)."`.
    - Sentence 2 (if any candidate): `"The main differences are <label> (<A> x, <B> y), ..."`.
    - Sentence 3 (if roles differ): the role warning.
- `exception/SamePlayerComparisonException`, `exception/ComparisonPlayerNotFoundException extends PlayerNotFoundException` (carries the message `"Player A not found."` / `"Player B not found."`; if both unknown, A is named).
- `service/PlayerComparisonService` also takes `PlayerService` in its constructor (existing unit tests are adapted: `PlayerService` mocked) and gets `PlayerComparison compareByName(String nameA, String nameB)`:
  - Each name is resolved in order A then B with `PlayerService.search(name)` (same matching rules as the player search, FR-15).
  - Blank name (`InvalidSearchException` from the search) -> new `BlankComparisonNameException`: `"Player A name must not be blank."` / `"Player B ..."`.
  - 0 match -> existing `ComparisonPlayerNotFoundException`: `"Player A not found."` / `"Player B not found."`.
  - Several matches: if exactly one match has `NameNormalizer.normalize(name)` equal to the normalized query, that player is used (exact match first; only the players returned by the search, i.e. the first 50, are examined). Otherwise -> new `AmbiguousPlayerNameException`: `"Several players match the name of player A: <name> (<teamCode>), ... and N more. Please be more precise."` - at most 10 candidates (alphabetical order as returned by the search), `N = total - 10`, and the "and N more" part only when N > 0.
  - Both resolved -> `compare(idA, idB)`, so FR-2 (same player) and the report are identical to the by-id path.
  - The two new exceptions are plain `RuntimeException`s in `exception/`; no HTTP mapping (no by-name endpoint, out of scope).
- `controller/PlayerController`: `@GetMapping("/compare") compare(@RequestParam("playerA") int, @RequestParam("playerB") int)` delegating to the service. The literal path wins over `/{id}`; a MockMvc test guards it.
- `controller/ApiExceptionHandler` (existing messages unchanged):
  - `SamePlayerComparisonException` → 400 `{"error":"The two players must be different."}`.
  - `ComparisonPlayerNotFoundException` → 404 with its own message (more specific than the existing `PlayerNotFoundException` handler).
  - Missing `playerA`/`playerB` → 400 `"Both playerA and playerB are required."`; non-integer value for them → 400 `"Player ids must be integers."`. The existing handlers for the search `name` parameter and for `/{id}` are kept as they are (branching on the parameter name).

### Frontend (`frontend/src/app`)

- `player/player.models.ts`: add `PlayerComparison`, `ComparedPlayer`, `ComparisonLine`, `ComparisonSummary`, `PlayerRole`, `Leader`.
- `player/player-api.service.ts`: add `compare(idA, idB): Observable<PlayerComparison>`.
- `player/player-picker/`: unchanged; still used by `PlayerSearchPage` only (the comparison page no longer uses it).
- `player/player-comparison-page/` (reworked): standalone page with two text fields (`data-testid` `name-a`, `name-b`, labels "Player A" / "Player B") and a single always-enabled "Compare" button. Per field a state `{ text, chosen: PlayerSummary | null, candidates: PlayerSummary[], error: string | null }`.
  - On "Compare": the report and the previous errors are cleared; a blank field gets "Please enter a player name." and no request is sent; for each non-blank field without a `chosen` player, `PlayerApiService.search(text)` is called (the two searches in parallel, `forkJoin`); result handling per field: `total === 0` -> error "Player not found."; `total === 1` -> `chosen`; `total > 1` -> `candidates` list under the field (list `candidates-a` / `candidates-b`, rows `candidate-row`; if `truncated`, the message "More than 50 players match: please refine your search." as in the search page). Search errors are shown under the field with `error.error.error` or `GENERIC_ERROR`.
  - Exact match first: when `total > 1` and exactly one player of `players` has a name equal to the typed text after normalisation (`trim`, collapse spaces, lower-case, strip accents via `normalize('NFD')`), that player becomes `chosen` and no candidate list is shown. Limitation: only the first 50 results returned by the search are examined.
  - Clicking a candidate sets `chosen` for that field (and the field text to the player's name) and clears its candidates; the user presses "Compare" again.
  - Editing a field's text clears that field's `chosen`, candidates and error, and clears the report.
  - When both fields have a `chosen` player after resolution, `PlayerApiService.compare(a.id, b.id)` is called; API errors (e.g. same player twice) are shown in the page `message`. Then: both identities, role warning (`.message` style), summary, verdict, and the list of lines. Values `null` shown as "not available". A pending request is cancelled when a new "Compare" starts or a field is edited.
- Bars (FR-13), per line with a leader: a diverging bar centred between the A and B values; the leader's half is filled, width = relative gap `|A − B| / max(|A|, |B|)` in %, with the difference text. Works for "lower is better" lines because it uses the gap, not the raw value. `TIE` → neutral marker with no fill. No leader / unavailable → no bar. Both values 0 → tie, width 0 (no division by zero). Classes `bar-a`, `bar-b`, `bar-tie`; `data-testid` on lines and bars.
- `app.routes.ts`: add `{ path: 'compare', component: PlayerComparisonPage, title: 'Player comparison' }`; `app.html`: a minimal nav with "Search" and "Compare" links.

### Data

No change to `data/donneesStats.json`, the schema or the repository.

## Data model and contracts

`GET /api/players/compare?playerA={id}&playerB={id}` → 200:

```json
{
  "playerA": {"id": 1, "name": "...", "teamName": "Mexico", "teamCode": "MEX", "role": "GOALKEEPER"},
  "playerB": {"id": 2, "name": "...", "teamName": "...", "teamCode": "...", "role": "OUTFIELD_PLAYER"},
  "lines": [{"key": "goals", "label": "Goals", "valueA": 0, "valueB": 3, "difference": -3, "leader": "B", "available": true}],
  "summary": {"leadsA": 4, "leadsB": 7, "ties": 1},
  "roleWarning": "…" ,
  "verdict": "…"
}
```

Errors: `{"error": "<message>"}` with 400 (same player, missing or non-integer ids) or 404 (unknown player), 503 when data is unavailable (existing handler).

## Spec traceability

| Requirement | Covered by |
|---|---|
| FR-1 | `PlayerComparisonService.compare`, `GET /compare` |
| FR-2 | Same-id check, `SamePlayerComparisonException`, 400 handler |
| FR-3 | `ComparisonPlayerNotFoundException`, 404 handler (names A or B) |
| FR-4 | `ComparedPlayer`, `PlayerRole` derived from `goalkeeperMatches` |
| FR-5 | Ordered statistics table, `ComparisonLine`, rounded difference |
| FR-6 | `Direction` per statistic, leader computation |
| FR-7 | `available = false` lines, excluded from summary and verdict |
| FR-8 | `ComparisonSummary` |
| FR-9 | `roleWarning` |
| FR-10 | `ComparisonVerdict.build` (deterministic) |
| FR-11 | `PlayerController#compare` + bean callable from Java, no HTTP types in the service |
| FR-12 | `PlayerComparisonPage` (two fields, single button, search then compare) |
| FR-12b | `PlayerComparisonPage` per-field states (not found, candidates list, blank) |
| FR-15 | `PlayerComparisonService#compareByName`, `BlankComparisonNameException`, `AmbiguousPlayerNameException` |
| FR-13 | Diverging bars in `PlayerComparisonPage` |
| FR-14 | English messages, labels, warning and verdict |

## Risks and alternatives rejected

- **Routing `/compare` vs `/{id}`**: literal path should win; covered by a MockMvc test.
- **Error handler regressions**: handlers branch on the parameter name; existing strict tests (`PlayerControllerTest`, `ApiExceptionHandlerTest`) must stay green.
- **Float artefacts** in differences: rounded to 2 decimals.
- **`PlayerComparisonService` constructor change** (adds `PlayerService`): existing unit tests and the Spring context must still build the bean; the by-id path stays unchanged.
- **Name resolution in two places** (browser via the search endpoint, Java via `PlayerService.search`): both use the same search, so the matching rules cannot drift.
- **Stale `chosen` player**: handled by clearing it whenever the field text is edited.
- Rejected: an HTTP endpoint by names (out of scope: Angular reuses the search endpoint), auto-picking the first match (risk of comparing the wrong player), a method on `PlayerService` (class bloat), a chart library (new dependency, poor jsdom testability), an LLM-written verdict (non-deterministic, out of scope), 422 for same ids (400 is consistent with `InvalidSearchException`).
