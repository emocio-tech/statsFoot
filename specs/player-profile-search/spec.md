> **Amended by `specs/player-h2-database/`**: the data source (JSON files replaced by an H2 database), the profile content (new statistics, integer player ids) and the API contract changed. The search behaviour described here is unchanged.

# Player profile search

## Context and goal

A casual fan of the 2026 World Cup wants to look up their favourite player and see everything the application knows about them: who they are and how they performed during the tournament. Today there is no way to do this.

The user types a player name in the web interface, the application looks the player up in its data, and the interface displays the player profile and tournament statistics.

Player data currently comes from the JSON files in `data/`. It will later come from a database, so the application must be able to switch its data source without changing what the user sees. The database source is built in this feature but is **not active**.

Scope is the 2026 World Cup only (see "Dans le périmètre" in `CLAUDE.md`).

## User stories

- As a fan, I want to type a player name so that I can find my favourite player quickly, without knowing the exact spelling.
- As a fan, I want to see the player's identity and tournament statistics on one page so that I get the full picture at a glance.
- As a fan, I want to be told clearly when no player matches so that I know whether I mistyped or the player is not in the tournament.
- As a maintainer, I want the data source to be replaceable (JSON now, database later) so that the move to a database does not impact the user interface.

## Functional requirements

- FR-1: The interface must offer a text field where the user enters a player name and submits a search.
- FR-2: The search must be case-insensitive, accent-insensitive and match any part of the player name (e.g. `suz` finds `Sami Suzuki`).
- FR-3: The search must ignore leading, trailing and repeated spaces, and must reject an empty or blank query with a clear message instead of listing every player.
- FR-4: When exactly one player matches, the interface must display that player's profile directly.
- FR-5: When several players match, the interface must list the matching players (name, team, position) and let the user pick one to display the profile. The list is capped at 50 results, ordered alphabetically by name; if more players match, the interface must tell the user to refine the search.
- FR-6: When no player matches, the interface must display a "player not found" message.
- FR-7: The player profile must show the identity data: name, team (full name and code, e.g. "Spain (ESP)"), shirt number, position (with detail), birth date, current age, club, height, preferred foot and whether the player is captain. The age is computed from the birth date and the current date at display time.
- FR-8: The player profile must show all tournament statistics available for the player: appearances, starts, minutes played, goals, assists, own goals, shots, shots on target, yellow cards, red cards, fouls committed, fouls suffered, passes and pass accuracy.
- FR-9: For goalkeepers, the profile must also show goalkeeper statistics: saves, goals conceded, clean sheets and penalties saved. For other players this section must not be displayed.
- FR-10: A statistic with no value (for instance pass accuracy for a player with no pass) must be displayed as "not available", never as `0` or blank.
- FR-11: The player data must be read from the JSON data files, and the search and profile must reflect those files at application start. The location of the data files must be configurable (default: the `data/` folder). The JSON data files are not part of the content delivered by this feature: they must not be added to `main` nor to the feature branch.
- FR-12: The application must provide a second, database-backed way of reading player data, interchangeable with the JSON one and returning identical results, but it must not be enabled or used in this feature. Enabling it must not require any change to the user interface.
- FR-13: If the player data cannot be read, the interface must display an explicit error message and the application must not crash.
- FR-14: All user-facing text and error messages must be in English.

## Acceptance criteria

- AC-1 (FR-1): Given the home page, When the user opens it, Then a name field and a search action are visible.
- AC-2 (FR-2): Given a player named `Sami Suzuki`, When the user searches `suz`, `SUZUKI` or `sámi`, Then that player is among the results.
- AC-3 (FR-3): Given the search field, When the user submits `  sami   suzuki  `, Then the search behaves as for `sami suzuki`.
- AC-4 (FR-3): Given the search field, When the user submits an empty or blank value, Then a "please enter a player name" message is shown and no player is listed.
- AC-5 (FR-4): Given a query matching exactly one player, When the user submits it, Then that player's profile is displayed without an intermediate list.
- AC-6 (FR-5): Given a query matching several players, When the user submits it, Then a list with name, team and position is displayed, and selecting an entry displays that player's profile.
- AC-7 (FR-5): Given a query matching more than 50 players, When the user submits it, Then only 50 are listed, ordered by name, with a message asking to refine the search.
- AC-8 (FR-6): Given a query matching no player, When the user submits it, Then a "player not found" message is displayed.
- AC-9 (FR-7): Given a displayed profile, When the user reads it, Then every identity field of FR-7 is shown with the value from the data, the team is shown as full name and code, and the birth date is shown.
- AC-9b (FR-7): Given a player born on 2006-11-03, When the profile is displayed on 2026-10-20, Then the age shown is 19; and when displayed on 2026-11-03, it is 20.
- AC-10 (FR-8): Given a displayed profile, When the user reads it, Then every statistic of FR-8 is shown with the value from the data.
- AC-11 (FR-9): Given a goalkeeper profile, When it is displayed, Then the goalkeeper statistics section is shown; and given an outfield player, Then it is not.
- AC-12 (FR-10): Given a player whose pass accuracy is `null`, When the profile is displayed, Then "not available" is shown for that statistic.
- AC-13 (FR-11): Given the JSON files, When a player is searched, Then the displayed values equal those in the JSON.
- AC-13b (FR-11): Given a data folder configured elsewhere than `data/`, When the application starts, Then the players are read from that folder; and the feature branch contains no JSON data file from `data/`.
- AC-14 (FR-12): Given the same player, When read through the JSON source and through the database source, Then both return identical data; and by default the application uses the JSON source only.
- AC-15 (FR-13): Given an unreadable or missing data file, When the user searches, Then an explicit error message is displayed and the application keeps running.
- AC-16 (FR-14): Given any screen or error of this feature, When displayed, Then its text is in English.

## Out of scope

- Statistics from other competitions or other World Cup editions.
- Comparing players with each other, ranking players, and team profiles (separate features).
- Match-by-match statistics of a player (only tournament totals are shown).
- Authentication or user accounts: "connecting to the application" means opening it.
- Switching the application to the database at runtime and importing data into the database (the database source exists but is unused).
- Editing player data.
- Player photos, favourites and internationalisation (English only).

## Open questions

- None.
