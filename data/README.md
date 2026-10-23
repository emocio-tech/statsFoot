# Data files

## Current data

- `donneesStats.json`: reference snapshot of the tournament data of the 2026 World Cup (competition, 48 teams, 1039 players with their tournament totals, 104 matches with line-ups, substitutions, goals, bookings, penalty shootouts, team and player ratings and detailed statistics). It is an export of the PostgreSQL database. The application does **not** import it: it is only read by the tests, which compare the API with it.
- `donneesStats.sql`: PostgreSQL reference of the schema, of the ratings computation and of the tournament views. The application database is created by Flyway (`backend/src/main/resources/db/migration`) and filled from `worldcup-full.json` and `donneesStats.sql` (profile `embedded`).

## Legacy files (no longer used by the code)

The sections below describe `players.json`, `teams.json` and the files they were generated with (`scripts/generate_test_data.py`, `matches.json`, `match_player_stats/`, which are not in the repository any more). They are kept as they were written, in French. The application does not read them any more.

### Données Coupe du Monde 2026 (TEST)

Données **fictives** générées par `scripts/generate_test_data.py` (`py scripts/generate_test_data.py`, graine fixe).
Les noms de joueurs, résultats et stats sont inventés ; le tableau final est simplifié (pas le tableau officiel FIFA).

#### Conventions

- Clés étrangères : `teamId` = code FIFA (`FRA`), `playerId` = `P-0001`, `matchId` = `M001`, `stadiumId` = `S-NYNJ`, `refereeId` = `R-001`, `coachId` = `C-001`.
- `score.fullTime` = score après 90 min ; `extraTime` = buts marqués **pendant** la prolongation (`null` si non jouée) ; `penalties` = tirs au but (`null` si non joués).
- `decidedBy` : `regular`, `extra_time` ou `penalties`. `winnerTeamId` est `null` pour un nul en phase de groupes.
- `stage` : `group`, `round_of_32`, `round_of_16`, `quarter_final`, `semi_final`, `third_place`, `final`.
- Événements : `goal` (`detail` : `open_play`, `header`, `penalty`, `free_kick`), `own_goal` (`teamId` = équipe créditée du but, `playerId` = auteur du CSC), `yellow_card`, `red_card`, `substitution`, `penalty_missed`, `var`.
- `teams[].stats` : buts incluant la prolongation ; `groupPoints` = points de la phase de groupes ; un match aux tirs au but compte comme nul.
- `teams[].finalPosition` : `champion`, `runner_up`, `third`, `fourth`, `quarter_final`, `round_of_16`, `round_of_32`, `group_stage`.
- `players[].stats` et `teams[].stats` sont des agrégats recalculables depuis `matches.json` et `match_player_stats/`.
