# Données Coupe du Monde 2026 (TEST)

Données **fictives** générées par `scripts/generate_test_data.py` (`py scripts/generate_test_data.py`, graine fixe).
Les noms de joueurs, résultats et stats sont inventés ; le tableau final est simplifié (pas le tableau officiel FIFA).

## Conventions

- Clés étrangères : `teamId` = code FIFA (`FRA`), `playerId` = `P-0001`, `matchId` = `M001`, `stadiumId` = `S-NYNJ`, `refereeId` = `R-001`, `coachId` = `C-001`.
- `score.fullTime` = score après 90 min ; `extraTime` = buts marqués **pendant** la prolongation (`null` si non jouée) ; `penalties` = tirs au but (`null` si non joués).
- `decidedBy` : `regular`, `extra_time` ou `penalties`. `winnerTeamId` est `null` pour un nul en phase de groupes.
- `stage` : `group`, `round_of_32`, `round_of_16`, `quarter_final`, `semi_final`, `third_place`, `final`.
- Événements : `goal` (`detail` : `open_play`, `header`, `penalty`, `free_kick`), `own_goal` (`teamId` = équipe créditée du but, `playerId` = auteur du CSC), `yellow_card`, `red_card`, `substitution`, `penalty_missed`, `var`.
- `teams[].stats` : buts incluant la prolongation ; `groupPoints` = points de la phase de groupes ; un match aux tirs au but compte comme nul.
- `teams[].finalPosition` : `champion`, `runner_up`, `third`, `fourth`, `quarter_final`, `round_of_16`, `round_of_32`, `group_stage`.
- `players[].stats` et `teams[].stats` sont des agrégats recalculables depuis `matches.json` et `match_player_stats/`.
