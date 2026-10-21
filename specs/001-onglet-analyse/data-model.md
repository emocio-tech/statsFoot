# Phase 1 – Modèle de données : Onglet Analyse

Origine des données (constitution VI) : **R** réelle, **C** calculée, **F** fictive.

## 1. Évolutions du schéma (migration V4, recopiée dans donneesStats.sql)

### `match_lineup` – colonne ajoutée

| Colonne | Type | Origine | Règle |
|---------|------|---------|-------|
| `is_goalkeeper` | BOOLEAN NOT NULL DEFAULT FALSE | R | Vrai pour le premier titulaire de la composition (import). Rétro-rempli par la V4 avec la règle de la partie 6. Au plus un gardien titulaire par équipe et par match. |

Contrainte : index unique partiel `(match_id, team_id) WHERE is_goalkeeper`.

### `goal_assist` – nouvelle table (F)

| Colonne | Type | Règle |
|---------|------|-------|
| `goal_id` | INT PK → `goal(id)` ON DELETE CASCADE | Au plus une passe décisive par but. |
| `player_id` | INT NOT NULL → `player(id)` | Coéquipier du buteur, présent sur le terrain à la minute du but, différent du buteur. |

Règles de validation (vérifiées par les tests) : aucun but avec `is_penalty` ou `is_own_goal`
n'a de passe ; `player.team_id` du passeur = `goal.team_id` ; le passeur est entré en jeu dans le
match et était sur le terrain à l'instant du but.

Remplissage : partie 6 de `donneesStats.sql` (DELETE puis INSERT, déterministe).

### `v_player_tournament_stats` – colonnes ajoutées à la fin

| Colonne | Origine | Définition |
|---------|---------|------------|
| `assists` | F | Nombre de `goal_assist` du joueur ; NULL si la table est vide. |
| `second_yellows` | R | Nombre de cartons `Y/R`. |
| `direct_reds` | R | Nombre de cartons `R`. |
| `fair_play_points` | C | Somme : `Y` -1, `Y/R` -2, `R` -4 (≤ 0). |
| `decisive_goals` | C | Somme de `player_match_rating.decisive_goals`. |
| `is_goalkeeper` | R | Gardien d'au moins un match (`match_lineup.is_goalkeeper`). |

### `v_team_tournament_stats` – colonnes ajoutées à la fin

| Colonne | Origine | Définition |
|---------|---------|------------|
| `stage_reached` | R | Phase du dernier match joué (`GROUP` … `FINAL`). |
| `final_position` | R | 1 champion (vainqueur de la finale, tirs au but compris), 2 finaliste, 3 vainqueur du match pour la 3e place, 4 perdant de ce match, 5 quart de finale, 6 huitième, 7 seizième, 8 phase de groupes. |

Vainqueur d'un match : `result = 'W'`, ou `result = 'D'` et `won_shootout`.

### Origine exposée par l'API (constitution IV et VI)

Les lignes renvoyées par `/api/stats/players` et `/api/stats/teams` (DTO `PlayerTournamentStats`,
`TeamTournamentStats`) gagnent un champ `fictitiousFields` : liste constante des champs d'origine
F (`foulsCommitted`, `freeKicksWon`, `freeKicksConceded`, `duelsWon`, `duelsLost`, `corners`,
`offsides`, `distanceKm`, et pour les joueurs `manOfTheMatchCount`, `manOfTheMatch`).

### Limite connue

Un gardien entré en jeu en cours de match n'a pas `is_goalkeeper` ; s'il n'a été titulaire dans
les buts d'aucun match, il est traité comme joueur de champ.

## 2. Catalogue des critères (code backend, non stocké)

**Question / critère** – une entrée par question, liste fermée :

| Champ | Description |
|-------|-------------|
| `id` | Identifiant stable, utilisé dans l'URL : `TOP_SCORER`, `TOP_ASSIST`, `MOST_YELLOW_CARDS`, `LEAST_FAIR_PLAY_PLAYER`, `BEST_TEAM`, `TOP_SCORING_TEAM`, `LEAST_FAIR_PLAY_TEAM`, `MOST_FAIR_PLAY_PLAYER`. |
| `label` | Libellé affiché (« Meilleur buteur »...). |
| `scope` | `PLAYER` ou `TEAM`. |
| `population` | `ALL_PLAYERS` ou `OUTFIELD_PLAYERS` (gardiens exclus). |
| `filter` | Condition supplémentaire (ex. aucun carton pour le plus fair-play). |
| `value` | Colonne affichée dans la réponse et unité (« buts », « passes décisives »...). |
| `order` | Tri principal puis départage(s). |
| `fictitious` | Vrai si le tri principal ou un départage utilise une donnée F. |
| `keywords` | Mots-clés normalisés (sans accents, minuscules). |
| `explanation` | Phrase qui décrit le critère et le départage. |

Valeurs du catalogue :

| id | Population / filtre | Tri principal | Départage | F |
|----|---------------------|---------------|-----------|---|
| TOP_SCORER | tous | buts ↓ | minutes ↑ | non |
| TOP_ASSIST | tous | passes décisives ↓ | minutes ↑ | oui |
| MOST_YELLOW_CARDS | tous | jaunes ↓ | jaunes / 90 min ↓ | non |
| LEAST_FAIR_PLAY_PLAYER | tous | points fair-play ↑ (plus négatif d'abord) | minutes ↑ | non |
| BEST_TEAM | équipes | position finale ↑ (1 = champion) | note moyenne ↓ | non |
| TOP_SCORING_TEAM | équipes | buts marqués ↓ | différence de buts ↓ | non |
| LEAST_FAIR_PLAY_TEAM | équipes | points fair-play ↑ | — | non |
| MOST_FAIR_PLAY_PLAYER | joueurs de champ, aucun carton | minutes ↓ | — | non |

## 3. Objets exposés par l'API (DTO)

- **AnalyseQuestion** : `id`, `label`, `scope`, `fictitious`.
- **AnalyseAnswer** : `status` (`ANSWERED`, `AMBIGUOUS`, `NOT_RECOGNIZED`, `NO_DATA`), `question`
  (AnalyseQuestion, absent si non reconnue), `explanation`, `fictitious`, `winners` (liste de
  RankedEntry de rang 1), `top` (RankedEntry de rang ≤ 5), `candidates` (questions proposées en
  cas d'ambiguïté ou de non-reconnaissance).
- **RankedEntry** : `rank`, `scope`, `id` (joueur ou équipe), `name`, `team` (joueurs), `value`,
  `unit`, `tieBreakValue`, `valueLabel` (valeur en mots quand elle se lit mieux ainsi : « champion »,
  « finaliste »… pour la meilleure équipe ; null sinon).
- **PlayerReport** : identité (`playerId`, `name`, `teamId`, `team`, `goalkeeper`), `totals`
  (ligne de `v_player_tournament_stats`, avec les colonnes V4), `matches` (par match : `matchId`,
  date, phase, adversaire, score, minutes, buts, passes décisives, cartons, `rating`), `bestMatch`,
  `ranks` (par critère joueur : `questionId`, `label`, `rank`, `outOf` ; `rank` null = non classé, le
  joueur étant hors de la population du critère, par exemple un gardien pour le plus fair-play), `fictitiousFields` (liste des
  champs d'origine F).
- **TeamReport** : identité (`teamId`, `team`), `totals` (ligne de `v_team_tournament_stats`),
  `matches` (parcours : `matchId`, date, phase, adversaire, score, tirs au but, `rating`),
  `squad` (joueurs de l'équipe : ligne de `v_player_tournament_stats`), `topScorer`, `bestPlayer`,
  `fictitiousFields`.

## 4. États d'une réponse

```text
question saisie ──normalisation──> score par critère
   ├─ aucun mot-clé            → NOT_RECOGNIZED (candidates = 8 questions)
   ├─ plusieurs ex aequo       → AMBIGUOUS (candidates = critères à égalité)
   └─ un critère               → classement
                                   ├─ aucune ligne → NO_DATA
                                   └─ sinon        → ANSWERED
```
