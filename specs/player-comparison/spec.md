# Player comparison

## Context and goal

A casual fan of the 2026 World Cup wants to compare two players on their tournament statistics and understand who did better and by how much. Today the application only shows one player profile at a time (see `specs/player-profile-search/` and `specs/player-h2-database/`).

This feature adds a **comparison service** that takes two players and returns a **comparison report**: every statistic side by side, the difference between the players, who leads on each statistic, and a written verdict explaining the differences. The same report is used by:

- a web screen that displays it, with a bar chart showing, for each statistic, which player leads;
- the application's internal LLM assistant (chat), through a tool that the maintainer will write on top of this service (not part of this feature).

Scope is the 2026 World Cup only (see "Dans le périmètre" in `CLAUDE.md`).

## User stories

- As a fan, I want to type the names of two players and press one button so that I can see their tournament statistics side by side, without searching each player separately.
- As a fan, I want to see, for each statistic, who leads and by how much, so that I do not have to compute the differences myself.
- As a fan, I want a short written verdict so that I understand the comparison at a glance.
- As a fan, I want to be warned when the two players do not play the same role, so that I know the comparison is less meaningful.

## Functional requirements

- FR-1: The application must expose a service that takes two player identifiers and returns a comparison report for those two players.
- FR-2: The two players must be different. A request with the same player twice must be rejected with a clear message.
- FR-3: A request with an unknown player must be rejected with a clear "player not found" message that says which player is unknown.
- FR-4: The report must identify both players (id, name, team name and code) and the role of each (goalkeeper or outfield player). A player is a goalkeeper if they played at least one match as goalkeeper, otherwise an outfield player.
- FR-5: The report must contain one line per tournament statistic available in the player data: matches played, matches started, minutes played, goals, penalty goals, own goals, yellow cards, red cards, fouls committed, free kicks won, free kicks conceded, duels won, duels lost, corners, offsides, distance covered, man-of-the-match count, rating and weighted rating. Each line holds the value of player A, the value of player B and the difference (A minus B).
- FR-6: Each line must state which player leads (A, B or tie). The meaning of "leads" depends on the statistic: for statistics where less is better (own goals, yellow cards, red cards, fouls committed, free kicks conceded, duels lost, offsides) the lower value leads; for the others the higher value leads, except matches played, matches started, minutes played and distance covered, which are shown for context and never designate a leader.
- FR-7: When a statistic has no value for at least one of the two players, its line must report "not available", must have no difference and no leader, and must not be counted in the summary.
- FR-8: The report must contain a summary: for each player, the number of statistics they lead, and the number of ties.
- FR-9: When the two players do not have the same role, the report must contain a warning stating that the comparison is not very relevant because the roles differ. The comparison is still produced.
- FR-10: The report must contain a written verdict, in English, built from the report data: it names both players, states who leads on more statistics (or that they are level), and explains the main differences (the statistics with the largest relative gap, at most three), and repeats the role warning when it applies. The same data always produces the same verdict.
- FR-11: The comparison service must be exposed over HTTP as a read-only endpoint `GET /api/players/compare?playerA={id}&playerB={id}`, and its report must be returned as structured data (the lines, the summary, the warning and the verdict as separate fields). The service must also be callable directly from Java code, independently of HTTP, so that other components (such as an LLM tool) can reuse it.
- FR-12: The web interface must offer two text fields (one per player) and a single "Compare" button. The user types a name in each field and presses the button; there is no separate search button per field. On "Compare", the interface resolves each name with the existing player search (same matching rules: case-insensitive, accent-insensitive, partial match) and then displays the report: identity of both players, the role warning if any, the summary and the written verdict.
- FR-12b: During that resolution, for each field: if no player matches, the interface must show a "Player not found." error for that field and no report; if exactly one player matches, that player is used; if several players match but exactly one of them has a name equal to the typed text (compared case-insensitively, accent-insensitively and ignoring leading, trailing and repeated spaces), that player is used without asking (exact match first); if several players match and none or several have such an exact name, the interface must list the matching players (name, team) under the field and let the user click one. The comparison runs once both fields hold exactly one chosen player (the user presses "Compare" again after choosing). A blank field must show a "please enter a player name" error for that field.
- FR-13: For each statistic with a leader or a tie, the web interface must display a bar chart in which the bar is on the side of the leading player, with the difference visible. Statistics without a leader (FR-6, FR-7) are displayed without a bar.
- FR-14: All user-facing text, verdict and error messages must be in English.
- FR-15: The comparison service must also offer a by-name entry point, callable from Java code and independent of HTTP, taking two player names and returning the same report as the comparison by ids (FR-1). Each name is resolved with the same matching rules as the player search. If several players match but exactly one of them has a name equal to the given name (same comparison as the player search: case, accents and extra spaces ignored), that player is used (exact match first). Otherwise, if a name is blank, matches no player, or matches several players, the service must raise a clear error (naming which of the two names is concerned, and for several matches listing the candidates with name and team, capped at 10 and saying how many more there are); no report is returned. If both names resolve to the same player, FR-2 applies.

## Acceptance criteria

- AC-1 (FR-1): Given two existing different players, When the comparison is requested, Then a report for these two players is returned.
- AC-2 (FR-2): Given the same player id twice, When the comparison is requested, Then it is rejected with a message saying the players must be different, and no report is returned.
- AC-3 (FR-3): Given one unknown player id, When the comparison is requested, Then it is rejected with a "player not found" message that names the unknown player.
- AC-4 (FR-4): Given a player with `goalkeeperMatches` > 0 and another with 0, When compared, Then the report shows the roles "goalkeeper" and "outfield player" respectively, with name, team name and code of both.
- AC-5 (FR-5): Given two players, When compared, Then the report contains one line per statistic of FR-5, each with both values and the difference A minus B, equal to the data.
- AC-6 (FR-6): Given A has 5 goals and B has 3, Then goals leads A with difference +2; given A has 4 yellow cards and B has 2, Then yellow cards leads B; given equal values, Then the line is a tie.
- AC-7 (FR-6): Given different minutes played (or different distance covered), Then the line shows both values and the difference but no leader.
- AC-8 (FR-7): Given one player with no rating (`null`), When compared, Then the rating line is "not available", has no difference and no leader, and is not counted in the summary.
- AC-9 (FR-8): Given a report, Then the summary counts per player equal the number of lines they lead, and the tie count equals the number of tie lines.
- AC-10 (FR-9): Given a goalkeeper and an outfield player, When compared, Then the report contains the role warning and still contains all lines; given two outfield players, Then there is no warning.
- AC-11 (FR-10): Given a report where A leads on more statistics, Then the verdict names A as leading, mentions at most three statistics with the largest relative gap, and includes the warning text if roles differ; given equal counts, Then the verdict says the players are level; given the same input twice, Then the verdict is identical.
- AC-12 (FR-11): Given a valid request on the HTTP endpoint, Then the response is structured data with separate fields for lines, summary, warning and verdict; and the endpoint modifies nothing.
- AC-13 (FR-11): Given the comparison service called directly from Java code with two player ids, Then it returns the same report as the HTTP endpoint.
- AC-14 (FR-12): Given the comparison screen with two fields and a "Compare" button, When the user types two names each matching exactly one player and presses "Compare", Then the report is displayed with both identities, the warning (if any), the summary and the verdict, and no per-field search button exists.
- AC-17 (FR-12b): Given a name matching no player, When the user presses "Compare", Then "Player not found." is shown for that field and no report is displayed.
- AC-18 (FR-12b): Given a name matching several players, When the user presses "Compare", Then the candidates (name, team) are listed under that field; and When the user clicks one and presses "Compare" again, Then the report is displayed.
- AC-22 (FR-12b): Given a typed name equal to the full name of one player and contained in the names of other players (for instance `Messi` with `Messi Jr` also present), When the user presses "Compare", Then the exact player is used without a candidate list; and given a name that is equal to several players' names or to none of them, Then the candidate list is shown.
- AC-19 (FR-12b): Given a blank field, When the user presses "Compare", Then a "please enter a player name" error is shown for that field and no request for the report is sent.
- AC-20 (FR-15): Given two names each matching exactly one player (for instance with different case and accents), When the by-name service is called from Java code, Then it returns a report equal to the one for the corresponding ids.
- AC-23 (FR-15): Given a name equal to the full name of one player and contained in the names of other players, When the by-name service is called, Then the exact player is used and the report is returned; and given a name equal to several players' names or to none, Then the ambiguity error of AC-21 is raised.
- AC-21 (FR-15): Given a blank name, an unknown name, or a name matching several players, When the by-name service is called, Then it raises an error that names which name is concerned (A or B) and, for several matches, lists the candidates (at most 10, with the remaining count); and given both names resolving to the same player, Then the error of AC-2 is raised.
- AC-15 (FR-13): Given a line where A leads, Then its bar is on A's side; where B leads, on B's side; where tie, a neutral bar; and given a line without leader, Then no bar is displayed.
- AC-16 (FR-14): Given any screen, verdict or error of this feature, When displayed, Then its text is in English.

## Out of scope

- Statistics from other competitions or other World Cup editions, and inter-championship or inter-year comparisons (see "Hors périmètre" in `CLAUDE.md`).
- Comparing more than two players, comparing teams, and ranking players.
- Letting the user choose which statistics are compared (all available statistics are always compared).
- A global weighted score or rating of "who is the best".
- A verdict written by an LLM: the verdict is built deterministically from the data.
- Match-by-match statistics, player positions finer than goalkeeper / outfield player, and normalisation per minute played.
- The LLM tool (`@Tool`) wrapping the service: it is written by the maintainer outside this feature.
- An HTTP endpoint taking player names: the by-name entry point (FR-15) is a Java service method only; the web interface resolves names with the existing search endpoint.
- Authentication, saving or sharing a comparison.

## Open questions

- None.
