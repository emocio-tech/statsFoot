# Player data from an H2 database

This specification amends `specs/player-profile-search/spec.md`. The search behaviour (FR-1 to FR-6, FR-13, FR-14 of that spec) is unchanged. The requirements about the **data source** and the **content of the profile** (FR-7 to FR-12 of that spec) are replaced by the requirements below.

## Context and goal

Until now the application read players from `players.json` and `teams.json`. A new data set is available: `data/donneesStats.sql` describes the tournament database (competition, teams, players, matches, line-ups, substitutions, goals, bookings, penalty shootouts, ratings, detailed statistics) and `data/donneesStats.json` contains the matching data for the 2026 World Cup (48 teams, 1039 players, 104 matches), as exported from that database.

The goal is to store this data in an H2 database built from the schema of `donneesStats.sql`, to make the application read the players from that database only, and to show on the player profile the information the new data set provides (and no longer the information it does not provide).

`donneesStats.sql` is a PostgreSQL script that contains no data and does not run as is on H2; it is the **reference of the schema**. The data comes from `donneesStats.json`.

Scope is the 2026 World Cup only (see "Dans le périmètre" in `CLAUDE.md`).

## User stories

- As a fan, I want the profile of my favourite player to show how they actually played (minutes, goals, cards, duels, distance, rating, man-of-the-match awards) so that I can judge their tournament.
- As a fan, I want to find a player by typing a part of the name, as before.
- As a maintainer, I want the tournament data stored in a real database whose structure follows `donneesStats.sql`, so that future features can query matches, goals and cards.
- As a maintainer, I want the database to be created automatically from the data file, so that no manual step is needed to run the application.

## Functional requirements

### Database

- FR-1: The application must use an H2 database whose tables follow the schema of `donneesStats.sql` for the competition data: competition, team, stadium, referee, player, match, match line-up, substitution, goal, booking, penalty kick, player and team match ratings, player and team match statistics.
- FR-2: The database must hold all the data of `donneesStats.json`: the competition, the 48 teams (name and FIFA code), the stadiums, the referees, the 1039 players, the 104 matches with their score per period, line-ups, substitutions, goals, bookings and penalty shootouts, and the ratings and detailed statistics of every team and player in every match.
- FR-3: The database must be stored in a file on the local machine, in a configurable location (default: `data/h2/`), that is not versioned.
- FR-4: When the database file does not exist or is empty, the application must create it and fill it from `data/donneesStats.json` at startup. When it already holds the data, the application must reuse it without importing again.
- FR-5: The import must be all-or-nothing: if it fails, the database must not be left half filled, and the next startup must try the import again.
- FR-6: The location of the data file (`donneesStats.json`) must be configurable (default: `data/donneesStats.json`).
- FR-7: The database must provide, for each player, the tournament totals defined by the view `v_player_tournament_stats` of `donneesStats.sql`: matches played and started, minutes, goals, penalty goals, own goals, yellow and red cards, fouls committed, free kicks won and conceded, duels won and lost, corners, offsides, distance, man-of-the-match count, average rating and rating weighted by minutes.
- FR-8: The players must be read from the database only. `players.json` and `teams.json` must no longer be used by the application, and the code that reads them is removed.

### Search (same behaviour, new data)

- FR-9: The search by player name must behave as in the previous spec (case-, accent- and space-insensitive partial match, 50 results maximum sorted by name, `total` and `truncated`), now on the 1039 players of the database.
- FR-10: The search result list must show, for each player, the name and the team (name and FIFA code). The position is no longer shown, since the data does not provide it.

### Profile

- FR-11: The player profile must show the identity data available in the new data: name, team (full name and FIFA code), number of matches as goalkeeper, number of matches as captain.
- FR-12: The profile must show the tournament statistics of FR-7 (all of them, with their labels in English).
- FR-13: A statistic with no value (for instance the weighted rating of a player who did not play) must be displayed as "not available", never as `0` or blank; a value of `0` must be displayed as `0`.
- FR-14: The profile must no longer show the data the new data set does not provide: shirt number, position and position detail, birth date and age, club, height, preferred foot, assists, shots, shots on target, passes, pass accuracy, and goalkeeper statistics (saves, goals conceded, clean sheets, penalties saved). The goalkeeper block is removed from the profile.
- FR-15: A profile is identified by the integer identifier of the player in the database; an unknown identifier gives "player not found".

### Errors and language

- FR-16: If the database cannot be opened or the import fails, the interface must display an explicit error message ("Player data is currently unavailable.") and the application must not crash.
- FR-17: All user-facing text and error messages must be in English.

## Acceptance criteria

- AC-1 (FR-1): Given a fresh database, When the application has started, Then the tables of the competition data exist with the columns of `donneesStats.sql`.
- AC-2 (FR-2): Given the import of `donneesStats.json`, Then the database holds 1 competition, 48 teams, 1039 players and 104 matches, and the number of line-ups, substitutions, goals, bookings, penalty kicks and statistic rows equals the number found in the file.
- AC-3 (FR-2): Given a player of the file, When read from the database, Then their match statistics equal those of the file, and the sum of their minutes over their matches equals their tournament total.
- AC-4 (FR-3): Given the default configuration, When the application has started, Then the database file is created under `data/h2/` and `git status` does not list it.
- AC-5 (FR-4): Given no database file, When the application starts, Then the data is imported; and given an existing filled database, When the application restarts, Then no import is done and the data is unchanged and not duplicated.
- AC-6 (FR-5): Given a data file that is corrupted halfway, When the import is run, Then no data remains in the database, and when the file is fixed, the next startup imports it fully.
- AC-7 (FR-6): Given another path configured for the data file, When the application starts, Then the data is imported from that path.
- AC-8 (FR-7, FR-12): Given a player, When the profile is displayed, Then the tournament totals shown equal those of the `tournament` block of that player in the file.
- AC-9 (FR-8): Given the application code, Then no class reads `players.json` or `teams.json`, and the application starts and works without these two files.
- AC-10 (FR-9): Given the player `Raul RANGEL`, When the user searches `rangel`, `RANGEL` or `  raul   rangel  `, Then that player is found.
- AC-11 (FR-9): Given a query matching more than 50 players, When submitted, Then 50 players are listed, sorted by name, and the user is asked to refine the search.
- AC-12 (FR-10): Given a list of results, When displayed, Then each entry shows the name and the team name with its FIFA code, and no position.
- AC-13 (FR-11): Given a displayed profile, Then the name, the team (name and FIFA code), the number of goalkeeper matches and the number of captain matches are shown.
- AC-14 (FR-13): Given a player whose weighted rating is null, When the profile is displayed, Then "not available" is shown for it; and given a player with `0` goals, Then `0` is shown.
- AC-15 (FR-14): Given a displayed profile, Then none of the removed items of FR-14 is shown, and the goalkeeper statistics block does not exist.
- AC-16 (FR-15): Given an unknown player identifier, When the profile is requested, Then "player not found" is returned.
- AC-17 (FR-16): Given a database that cannot be opened, When the user searches, Then "Player data is currently unavailable." is displayed and the application keeps running.
- AC-18 (FR-17): Given any screen or error of this feature, Then its text is in English.

## Out of scope

- Team profile, ranking and comparison of players or teams, match pages (the database holds the match data, but no screen uses it yet).
- Running `donneesStats.sql` itself (ratings computation, fictitious statistics generation) on H2, or any other PostgreSQL feature of that script. The ratings and statistics are the ones already computed in `donneesStats.json`.
- Updating the data after the first import (no re-import command, no migration tool). To reload the data, the database file is deleted.
- Switching to another database engine (PostgreSQL) in production.
- Authentication, editing player data, internationalisation.

## Open questions

- None.
