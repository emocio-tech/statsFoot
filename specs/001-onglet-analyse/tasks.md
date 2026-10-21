---

description: "Tâches de l'onglet Analyse"
---

# Tasks: Onglet Analyse

**Input**: Design documents from `/specs/001-onglet-analyse/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/api.md, quickstart.md

**Tests**: inclus. La constitution (principe III) impose des tests backend pour toute
fonctionnalité qui touche la notation, les vues statistiques, les critères d'analyse ou l'API,
avec, pour chaque critère, un test du résultat et un test de la règle d'égalité.

**Organization**: tâches groupées par histoire utilisateur ; toutes sur la branche
`initDonneesNotation` (constitution V), un commit par groupe logique, tests au vert à chaque commit
(hook pre-commit).

**Révision du 21/10/2026** : corrections issues de `/speckit-analyze` (C1 à C5, H1) et des
décisions de clarification (meilleure équipe = champion, fair-play en points FIFA, stats fictives
réservées au meilleur passeur et aux rapports).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallélisable (fichiers différents, pas de dépendance sur une tâche inachevée)
- **[Story]**: histoire concernée (US1, US2, US3)
- Chemins : `backend/src/...`, `frontend/src/...` (application web, voir plan.md)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: préparer le paquet et les helpers communs

- [X] T001 Créer le paquet `com.emocio.analysefoot.analyse` avec un `package-info.java` décrivant son rôle (catalogue des critères et reconnaissance des questions) dans backend/src/main/java/com/emocio/analysefoot/analyse/package-info.java
- [X] T002 [P] Ajouter dans backend/src/test/java/com/emocio/analysefoot/WorldCupImportAndRatingTest.java une méthode `runDonneesStatsDataParts()` qui lit `src/main/resources/db/migration/donneesStats.sql` (chemin relatif au module backend), n'en garde **que** les parties de données — de la ligne `-- PARTIE 3 ` exclue à la ligne `-- PARTIE 4 ` exclue, puis de la ligne `-- PARTIE 6 ` à la fin — retire les lignes commençant par `\` et exécute le résultat avec `ScriptUtils.executeSqlScript`, sur le modèle de `runRatingScript()`. Les parties de schéma 1, 2, 4 et 5 ne sont jamais exécutées sur une base gérée par Flyway (constitution I, research R8)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: schéma V4, gardien réel, passes décisives fictives, position finale, vues complétées et signalement des données fictives existantes, dont dépendent les trois histoires

**⚠️ CRITICAL**: aucune histoire ne peut commencer avant la fin de cette phase

- [X] T003 Créer backend/src/main/resources/db/migration/V4__analyse.sql : (1) `ALTER TABLE match_lineup ADD COLUMN is_goalkeeper BOOLEAN NOT NULL DEFAULT FALSE` ; (2) rétro-remplissage par `UPDATE match_lineup` avec la règle de la partie 6 de donneesStats.sql (n°1 = `MIN(player.id)` de l'équipe s'il est titulaire, sinon le titulaire jamais aligné en même temps que lui, plus petit id) ; (3) index unique partiel `(match_id, team_id) WHERE is_goalkeeper` ; (4) table `goal_assist (goal_id INT PRIMARY KEY REFERENCES goal(id) ON DELETE CASCADE, player_id INT NOT NULL REFERENCES player(id))` et index sur `player_id` ; (5) `CREATE OR REPLACE VIEW v_player_tournament_stats` identique à la V3 avec, **ajoutées à la fin**, `assists` (NULL si `goal_assist` est vide, sinon nombre, 0 compris), `second_yellows` (cartons `Y/R`), `direct_reds` (cartons `R`), `fair_play_points` (`Y` -1, `Y/R` -2, `R` -4), `decisive_goals` (somme de `player_match_rating.decisive_goals`), `is_goalkeeper` (gardien d'au moins un match) ; (6) `CREATE OR REPLACE VIEW v_team_tournament_stats` identique à la V3 avec, **ajoutées à la fin**, `stage_reached` (phase du dernier match joué) et `final_position` (1 champion = vainqueur de la finale, tirs au but compris ; 2 finaliste ; 3 vainqueur du match pour la 3e place ; 4 perdant de ce match ; 5 quart ; 6 huitième ; 7 seizième ; 8 phase de groupes ; vainqueur = `result = 'W'` ou `result = 'D' AND won_shootout`) (data-model §1)
- [X] T004 Ajouter le champ `isGoalkeeper` (colonne `is_goalkeeper`, `boolean`, non nul) et son paramètre de constructeur dans backend/src/main/java/com/emocio/analysefoot/model/MatchLineup.java, pour rester valide avec `ddl-auto: validate` (constitution I)
- [X] T005 Dans backend/src/main/java/com/emocio/analysefoot/importer/WorldCupImporter.java (`importLineup`), passer `isGoalkeeper = true` pour le premier élément de `lineup.starter()` et `false` pour les autres titulaires et le banc (depends on T004)
- [X] T006 Recopier **entièrement** la V4 dans backend/src/main/resources/db/migration/donneesStats.sql (constitution I) : en-tête (parties et migration V4) ; partie 4 : `ADD COLUMN IF NOT EXISTS is_goalkeeper`, **le même `UPDATE` de rétro-remplissage que la V4**, l'index unique partiel, la table `goal_assist` et son index (`IF NOT EXISTS`) ; partie 5 : les deux vues de la V4 (`CREATE OR REPLACE VIEW`, colonnes ajoutées à la fin) ; la partie 6 reste la dernière (depends on T003)
- [X] T007 Dans la partie 6 de backend/src/main/resources/db/migration/donneesStats.sql, remplacer la table temporaire `fs_goalkeeper` par la lecture de `match_lineup.is_goalkeeper`, puis ajouter la génération des passes décisives : `DELETE FROM goal_assist` ; table temporaire des présences (entrée au coup d'envoi ou au remplacement, sortie au remplacement ou à l'exclusion, instants `minute × 100 + temps additionnel`) ; pour chaque but `NOT is_penalty AND NOT is_own_goal`, passe décisive si `pg_temp.rnd(goal_id, 'assist') < 0.75`, passeur = coéquipier présent sur le terrain à l'instant du but, différent du buteur, choisi par le poids le plus fort `pg_temp.rnd(player_id, 'creator') × (0.1 si gardien sinon 1) × pg_temp.rnd(goal_id, player_id, 'assist_pick')` ; commentaire des règles de cohérence (research R2, FR-003b) (depends on T006)
- [X] T008 [P] Ajouter à backend/src/main/java/com/emocio/analysefoot/dto/PlayerTournamentStats.java et backend/src/main/java/com/emocio/analysefoot/dto/TeamTournamentStats.java un composant `List<String> fictitiousFields` renseigné par une constante (joueurs : `foulsCommitted`, `freeKicksWon`, `freeKicksConceded`, `duelsWon`, `duelsLost`, `corners`, `offsides`, `distanceKm`, `manOfTheMatchCount`, `manOfTheMatch` ; équipes : les mêmes sans les deux derniers), adapter backend/src/main/java/com/emocio/analysefoot/service/TournamentStatsQueryService.java et vérifier le champ dans backend/src/test/java/com/emocio/analysefoot/controller/TournamentStatsControllerTest.java (constitution IV, FR-005b, research R10)
- [X] T009 [P] Signaler les colonnes fictives des onglets existants : ajouter `fictitiousFields` aux interfaces de frontend/src/app/core/models/rating.ts ; ajouter `fictitious?: boolean` à `StatsColumn` dans frontend/src/app/shared/stats-table.ts (astérisque dans l'en-tête, infobulle « donnée fictive ») ; dans frontend/src/app/features/players/player-stats.ts et frontend/src/app/features/teams/team-stats.ts, marquer les colonnes listées par `fictitiousFields` et remplacer le bandeau conditionnel par une légende permanente « * données fictives » (FR-005b, SC-004)
- [X] T010 Ajouter dans backend/src/test/java/com/emocio/analysefoot/WorldCupImportAndRatingTest.java un groupe `@Nested class DonneesStats` qui appelle `runDonneesStatsDataParts()` (T002) et vérifie : l'en-tête de donneesStats.sql cite chaque fichier `V{n}__*.sql` du dossier des migrations ; un seul gardien titulaire par équipe et par match, gardiens du jeu d'essai = A1, B1, G1 ; aucune passe décisive sur un penalty ni un but contre son camp ; chaque passeur est de l'équipe du but, différent du buteur et sur le terrain à l'instant du but ; deux exécutions donnent les mêmes passes (déterminisme) ; colonnes V4 (B2 : `second_yellows` = 1, `fair_play_points` = -3 ; B3 : `direct_reds` = 1, `fair_play_points` = -4 ; `final_position` : Alpha 1 (tirs au but), Gamma 2, Beta 8) (depends on T002, T003, T005, T007)
- [X] T011 Mettre à jour la base `analysefoot` : démarrer l'application (Flyway V4), puis exécuter **uniquement** les parties 3 et 6 de donneesStats.sql avec la commande de quickstart.md §2 ; exécuter ensuite les contrôles de cohérence des notes (constitution II : 3 288 notes joueurs, 208 notes équipes, moyennes proches de 6) et des données V4 (208 lignes `is_goalkeeper` vraies, aucune passe sur penalty ou but contre son camp, passeur coéquipier présent) (depends on T010)

**Checkpoint**: V4 appliquée, parties de données de donneesStats.sql exécutables sur le schéma migré, données fictives signalées dans les onglets existants, tests au vert — commit « V4 : gardien, passes décisives fictives, position finale ; données fictives signalées »

---

## Phase 3: User Story 1 - Répondre aux questions proposées (Priority: P1) 🎯 MVP

**Goal**: les 8 suggestions cliquables donnent la réponse, l'explication du critère, les ex aequo, le top 5 et la mention « données fictives » pour le meilleur passeur

**Independent Test**: cliquer sur chacune des 8 questions de `/analyse` et comparer aux valeurs du tableau de quickstart.md §3

### Tests for User Story 1

- [X] T012 [P] [US1] Créer backend/src/test/java/com/emocio/analysefoot/controller/AnalyseControllerTest.java (`@WebMvcTest(AnalyseController.class)`, service mocké) : `GET /api/analyse/questions` renvoie 8 questions dans l'ordre ; `GET /api/analyse/TOP_SCORER` renvoie `status`, `winners`, `top`, `explanation`, `fictitious` ; `GET /api/analyse/INCONNU` renvoie 404 `{"error": "Question inconnue."}` (contracts/api.md)
- [X] T013 [US1] Ajouter dans backend/src/test/java/com/emocio/analysefoot/WorldCupImportAndRatingTest.java un groupe `@Nested class Analyse` (après `runDonneesStatsDataParts()`) avec, **pour chaque critère, un test du vainqueur et un test de la règle de départage** sur worldcup-sample.json ; quand le jeu d'essai n'a pas d'égalité, le test la crée par `UPDATE`/`INSERT` dans la base de test (tables `player_match_rating`, `booking`, `goal_assist`, `match`, `team_match_rating`) : TOP_SCORER (vainqueur A1, 3 buts ; départage : donner 3 buts à un joueur ayant moins de minutes, qui passe devant) ; TOP_ASSIST (vainqueur = maximum de `goal_assist`, `fictitious` = true ; départage par les minutes avec une passe insérée) ; MOST_YELLOW_CARDS (égalité naturelle A2 / B2 à 1 jaune, départagée par jaunes / 90 min) ; LEAST_FAIR_PLAY_PLAYER (vainqueur B3, -4, devant B2, -3 ; départage : rouge direct ajouté à un joueur ayant plus de minutes que B3, B3 reste devant) ; BEST_TEAM (vainqueur Alpha, champion aux tirs au but ; départage : équipe Delta insérée avec un match de groupe contre Beta et ses lignes `team_match_rating`, Beta et Delta en position 8 départagées par la note) ; TOP_SCORING_TEAM (vainqueur Alpha ; égalité naturelle Beta / Gamma à 1 but, Gamma devant à la différence de buts) ; LEAST_FAIR_PLAY_TEAM (vainqueur Beta, -7 ; sans départage : un carton ajouté crée un ex aequo, les deux équipes sont au rang 1) ; MOST_FAIR_PLAY_PLAYER (vainqueur joueur de champ sans carton au plus de minutes, A1 gardien exclu ; sans départage : égalité de minutes → ex aequo) ; plus : `fictitious` = false pour les 7 critères autres que TOP_ASSIST ; `NO_DATA` quand `player_match_rating` est vide (depends on T010)

### Implementation for User Story 1

- [X] T014 [P] [US1] Créer les DTO records `AnalyseQuestion(String id, String label, String scope, boolean fictitious)`, `RankedEntry(int rank, String scope, Integer id, String name, String team, BigDecimal value, String unit, BigDecimal tieBreakValue)` et `AnalyseAnswer(String status, AnalyseQuestion question, String explanation, boolean fictitious, List<RankedEntry> winners, List<RankedEntry> top, List<AnalyseQuestion> candidates)` dans backend/src/main/java/com/emocio/analysefoot/dto/ (data-model §3 ; `status` ∈ `ANSWERED`, `AMBIGUOUS`, `NOT_RECOGNIZED`, `NO_DATA`)
- [X] T015 [P] [US1] Créer l'enum `AnalyseCriterion` dans backend/src/main/java/com/emocio/analysefoot/analyse/AnalyseCriterion.java avec les 8 entrées du tableau data-model §2 (ordre des suggestions : TOP_SCORER, TOP_ASSIST, MOST_YELLOW_CARDS, LEAST_FAIR_PLAY_PLAYER, BEST_TEAM, TOP_SCORING_TEAM, LEAST_FAIR_PLAY_TEAM, MOST_FAIR_PLAY_PLAYER) ; chaque entrée porte libellé, portée, vue source, filtre SQL (`NOT is_goalkeeper AND yellow_cards = 0 AND red_cards = 0` pour MOST_FAIR_PLAY_PLAYER), expression de valeur et unité, `ORDER BY` principal puis départage (BEST_TEAM : `final_position ASC, rating DESC` ; ratios avec `NULLIF(minutes_played, 0)` et `NULLS LAST`), `fictitious` (vrai pour TOP_ASSIST seulement), phrase d'explication, mots-clés (remplis en US3) ; les fragments SQL sont des constantes, jamais une saisie (research R4)
- [X] T016 [US1] Créer backend/src/main/java/com/emocio/analysefoot/analyse/AnalyseService.java : `List<AnalyseQuestion> questions()` ; `AnalyseAnswer answer(AnalyseCriterion)` qui exécute avec `JdbcClient` une requête `SELECT ..., RANK() OVER (ORDER BY <tri>, <départage>) AS rank FROM <vue> WHERE competition_id = (SELECT MAX(id) FROM competition) AND <filtre>` filtrée à `rank <= 5`, renvoie `winners` = rang 1, `top` = rang ≤ 5 (plus de 5 lignes en cas d'ex aequo), `NO_DATA` si aucune ligne (depends on T014, T015)
- [X] T017 [US1] Créer backend/src/main/java/com/emocio/analysefoot/controller/AnalyseController.java : `GET /api/analyse/questions` et `GET /api/analyse/{questionId}` (404 `{"error": "Question inconnue."}` pour un id hors catalogue ; le chemin littéral `questions` est prioritaire) (depends on T016)
- [X] T018 [P] [US1] Créer frontend/src/app/core/models/analyse.ts (interfaces `AnalyseQuestion`, `RankedEntry`, `AnalyseAnswer`, alignées sur les DTO de T014) et frontend/src/app/core/services/analyse.service.ts (`getQuestions()`, `answer(questionId)`)
- [X] T019 [US1] Créer frontend/src/app/features/analyse/analyse.ts : 8 suggestions chargées par `getQuestions()` et cliquables ; réponse en titre « <nom>, <valeur> <unité> » ; phrase d'explication ; mention « données fictives » si `fictitious` (et seulement dans ce cas) ; ex aequo listés ; top 5 affiché avec `app-stats-table` et `app-rating-badge` ; état de chargement et `NO_DATA` (« Aucune donnée importée ») (depends on T018)
- [X] T020 [US1] Ajouter la route `analyse` (titre « Analyse · Analyse Foot ») dans frontend/src/app/app.routes.ts et le lien « Analyse » dans le menu de frontend/src/app/app.ts (depends on T019)

**Checkpoint**: US1 démontrable seule — commit « Onglet Analyse : questions proposées »

---

## Phase 4: User Story 2 - Consulter le rapport complet (Priority: P2)

**Goal**: rapport joueur et rapport équipe sous la réponse, et en un clic depuis les onglets Joueurs et Équipes

**Independent Test**: ouvrir `/joueurs/664` (Mbappé) et `/equipes/29` (Espagne) depuis les onglets Joueurs et Équipes, puis sous une réponse de l'onglet Analyse, et vérifier chaque rubrique (spec US2)

### Tests for User Story 2

- [X] T021 [P] [US2] Créer backend/src/test/java/com/emocio/analysefoot/controller/ReportControllerTest.java (`@WebMvcTest(ReportController.class)`, service mocké) : 200 avec les champs du contrat pour `/api/players/{id}/report` et `/api/teams/{id}/report`, 404 pour un id inconnu, `fictitiousFields` présent (contracts/api.md)
- [X] T022 [US2] Ajouter dans le groupe `Analyse` de backend/src/test/java/com/emocio/analysefoot/WorldCupImportAndRatingTest.java des tests de `ReportQueryService` : rapport de A1 (2 matchs, 3 buts, notes par match triées par date, meilleur match = match de groupe, rang 1 sur TOP_SCORER) ; rapport d'Alpha (parcours de 2 matchs, `finalPosition` = 1, effectif, meilleur buteur A1) ; valeurs fictives absentes renvoyées `null` (depends on T013)

### Implementation for User Story 2

- [X] T023 [P] [US2] Créer les DTO records `PlayerReport`, `TeamReport` et leurs lignes (match du joueur, match de l'équipe, rang par critère, référence de joueur) dans backend/src/main/java/com/emocio/analysefoot/dto/, champs selon data-model §3 et contracts/api.md (dont `stageReached` et `finalPosition` dans les totaux d'équipe), avec `fictitiousFields` (liste des noms de champs d'origine fictive)
- [X] T024 [US2] Créer backend/src/main/java/com/emocio/analysefoot/service/ReportQueryService.java : `Optional<PlayerReport> playerReport(int id)` (totaux depuis `v_player_tournament_stats`, matchs depuis `player_match_rating` + `match` + `goal_assist` triés par `kickoff_at`, meilleur match = meilleure note, rangs sur chaque critère joueur de `AnalyseCriterion` avec `outOf` = taille de la population) et `Optional<TeamReport> teamReport(int id)` (totaux depuis `v_team_tournament_stats`, parcours depuis `match` + `team_match_rating`, effectif depuis `v_player_tournament_stats`, meilleur buteur, meilleur joueur selon `rating_weighted`) (depends on T023)
- [X] T025 [US2] Créer backend/src/main/java/com/emocio/analysefoot/controller/ReportController.java : `GET /api/players/{id}/report` et `GET /api/teams/{id}/report`, 404 si absent (depends on T024)
- [X] T026 [P] [US2] Ajouter les interfaces `PlayerReport`, `TeamReport` à frontend/src/app/core/models/analyse.ts et `getPlayerReport(id)`, `getTeamReport(id)` à frontend/src/app/core/services/analyse.service.ts
- [X] T027 [P] [US2] Créer frontend/src/app/features/reports/player-report.ts (entrée `playerId`, aussi lue depuis la route) : identité, totaux, stats détaillées marquées « données fictives » d'après `fictitiousFields`, notes par match avec lien `/matchs/:id`, meilleur match, rangs ; valeur `null` affichée « – » (FR-006) (depends on T026)
- [X] T028 [P] [US2] Créer frontend/src/app/features/reports/team-report.ts (entrée `teamId`, aussi lue depuis la route) : position finale, parcours avec scores, tirs au but, notes et liens `/matchs/:id`, totaux, stats détaillées marquées « données fictives », effectif (`app-stats-table`), meilleur buteur et meilleur joueur (depends on T026)
- [X] T029 [US2] Ajouter à frontend/src/app/shared/stats-table.ts une sortie optionnelle `rowClick` (émet la ligne cliquée ; curseur pointeur et activation au clavier seulement si la sortie est utilisée) (depends on T009)
- [X] T030 [US2] Ajouter les routes `joueurs/:id` et `equipes/:id` dans frontend/src/app/app.routes.ts, et brancher `rowClick` vers ces routes dans frontend/src/app/features/players/player-stats.ts et frontend/src/app/features/teams/team-stats.ts (depends on T020, T027, T028, T029)
- [X] T031 [US2] Afficher sous la réponse de frontend/src/app/features/analyse/analyse.ts le rapport du premier gagnant (`app-player-report` ou `app-team-report` selon `scope`) (depends on T019, T027, T028)

**Checkpoint**: US1 et US2 fonctionnent — commit « Rapports joueur et équipe »

---

## Phase 5: User Story 3 - Poser une question en texte libre (Priority: P3)

**Goal**: reconnaissance par mots-clés, ambiguïté signalée, question non reconnue → liste des questions

**Independent Test**: saisir 3 reformulations par question, « le moins fair-play » et « quel temps fera-t-il » (quickstart.md §3)

### Tests for User Story 3

- [X] T032 [P] [US3] Créer backend/src/test/java/com/emocio/analysefoot/analyse/QuestionMatcherTest.java : au moins 3 reformulations reconnues par question (SC-003), dont « qui a marqué le plus ? », « qui a pris le plus de jaunes », « meilleur passeur », « qui est champion » ; insensibilité aux accents et majuscules (« EQUIPE la moins fair play ») ; « le moins fair-play » → ambiguïté entre LEAST_FAIR_PLAY_PLAYER et LEAST_FAIR_PLAY_TEAM ; « quel temps fera-t-il demain ? » → non reconnue
- [X] T033 [P] [US3] Ajouter à backend/src/test/java/com/emocio/analysefoot/controller/AnalyseControllerTest.java : `GET /api/analyse?q=...` pour `ANSWERED`, `AMBIGUOUS` (`candidates` = 2 questions), `NOT_RECOGNIZED` (`candidates` = 8 questions) et 400 si `q` est vide (depends on T012)

### Implementation for User Story 3

- [X] T034 [US3] Renseigner les mots-clés normalisés (sans accents, minuscules) de chaque entrée de backend/src/main/java/com/emocio/analysefoot/analyse/AnalyseCriterion.java (BEST_TEAM : « meilleure equipe », « champion », « vainqueur »…), ainsi que les mots de portée (« joueur », « equipe », « selection », « pays »…)
- [X] T035 [US3] Créer backend/src/main/java/com/emocio/analysefoot/analyse/QuestionMatcher.java : normalisation (`java.text.Normalizer` NFD, suppression des diacritiques, minuscules, ponctuation → espaces), score = nombre de mots-clés présents, bonus de portée ; résultat reconnu (score maximal unique), ambigu (plusieurs ex aequo au score maximal) ou non reconnu (score nul) (research R5) (depends on T034)
- [X] T036 [US3] Ajouter `AnalyseAnswer answer(String question)` à backend/src/main/java/com/emocio/analysefoot/analyse/AnalyseService.java et `GET /api/analyse?q=` à backend/src/main/java/com/emocio/analysefoot/controller/AnalyseController.java (400 `{"error": ...}` si `q` absent ou vide) (depends on T035)
- [X] T037 [US3] Ajouter `search(q)` à frontend/src/app/core/services/analyse.service.ts et, dans frontend/src/app/features/analyse/analyse.ts, la barre de recherche : `ANSWERED` comme une suggestion, `AMBIGUOUS` → choix entre les `candidates`, `NOT_RECOGNIZED` → message et liste des questions disponibles (depends on T036)

**Checkpoint**: les trois histoires fonctionnent — commit « Onglet Analyse : questions libres »

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T038 [P] Mettre à jour README.md : tableau des routes avec `GET /api/stats/players`, `GET /api/stats/teams` (manquantes, avec `fictitiousFields`), `GET /api/analyse/questions`, `GET /api/analyse?q=`, `GET /api/analyse/{questionId}`, `GET /api/players/{id}/report`, `GET /api/teams/{id}/report` ; mention de la V4, de la génération des passes décisives fictives et de la commande qui n'exécute que les parties 3 et 6 de donneesStats.sql (constitution I et IV)
- [X] T039 [P] Mettre à jour le cadrage backend/src/main/resources/db/migration/cadrage.txt : décisions tranchées (passes fictives, gardiens exclus, mots-clés seulement, stats fictives pour le passeur et les rapports, meilleure équipe = champion, fair-play en points FIFA) ; V4 réalisée
- [X] T040 Lancer `./mvnw -pl backend test` puis `./mvnw clean package` (build Angular compris) et corriger toute erreur
- [X] T041 Dérouler le parcours manuel de specs/001-onglet-analyse/quickstart.md §3 sur http://localhost:8080 (y compris le temps de réponse de SC-002) et y reporter le meilleur passeur obtenu

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** : aucune dépendance.
- **Foundational (Phase 2)** : après Setup ; bloque toutes les histoires (gardien, passes, position finale et colonnes V4 sont utilisés par US1 et US2).
- **US1 (Phase 3)**, **US2 (Phase 4)**, **US3 (Phase 5)** : après Foundational.
- **Polish (Phase 6)** : après les histoires retenues.

### User Story Dependencies

- **US1 (P1)** : indépendante après Foundational.
- **US2 (P2)** : indépendante après Foundational pour les rapports et l'accès depuis Joueurs/Équipes (T021–T030) ; seules T030 (fichier de routes) et T031 (écran) suivent US1 (T019, T020).
- **US3 (P3)** : réutilise `AnalyseCriterion`, `AnalyseService` et l'écran de US1 (T015, T016, T019).

### Within Each User Story

- Tests d'abord (ils échouent), puis DTO et catalogue, services, contrôleurs, écrans.
- `WorldCupImportAndRatingTest.java` est partagé : T002, T010, T013, T022 s'y suivent, jamais en parallèle.
- `AnalyseCriterion.java` (T015, T034), `AnalyseService.java` (T016, T036), `AnalyseControllerTest.java` (T012, T033), `analyse.ts` (T019, T031, T037), `stats-table.ts` (T009, T029), `player-stats.ts` / `team-stats.ts` (T009, T030) et `app.routes.ts` (T020, T030) sont modifiés en séquence.

### Parallel Opportunities

- Phase 1 : T001 ∥ T002.
- Phase 2 : T008 ∥ T009, en parallèle de T003–T007 (fichiers différents).
- US1 : T012 en parallèle de T013 ; puis T014 ∥ T015 ∥ T018.
- US2 : T021 ∥ T023 ∥ T026 ; puis T027 ∥ T028.
- US3 : T032 ∥ T033.
- Polish : T038 ∥ T039.

---

## Parallel Example: User Story 1

```bash
# Test de contrôleur pendant les tests d'intégration :
Task: "AnalyseControllerTest dans backend/src/test/java/com/emocio/analysefoot/controller/AnalyseControllerTest.java"
Task: "Tests des 8 critères (vainqueur et départage) dans backend/src/test/java/com/emocio/analysefoot/WorldCupImportAndRatingTest.java"

# Puis DTO, catalogue et modèles front ensemble :
Task: "DTO AnalyseQuestion, RankedEntry, AnalyseAnswer dans backend/src/main/java/com/emocio/analysefoot/dto/"
Task: "Enum AnalyseCriterion dans backend/src/main/java/com/emocio/analysefoot/analyse/AnalyseCriterion.java"
Task: "Modèles et service Angular dans frontend/src/app/core/"
```

## Parallel Example: User Story 2

```bash
Task: "Rapport joueur dans frontend/src/app/features/reports/player-report.ts"
Task: "Rapport équipe dans frontend/src/app/features/reports/team-report.ts"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 puis Phase 2 (V4, donneesStats.sql, signalement des données fictives, tests de cohérence).
2. Phase 3 : les 8 questions proposées.
3. **STOP and VALIDATE** : parcours quickstart.md §3, lignes « Clic ».
4. Commit sur `initDonneesNotation`.

### Incremental Delivery

1. Fondations → commit.
2. US1 → validation → commit (MVP).
3. US2 → rapports depuis Analyse, Joueurs et Équipes → commit.
4. US3 → questions libres → commit.
5. Polish → README, cadrage, build complet, parcours manuel.

---

## Notes

- [P] = fichiers différents, sans dépendance ouverte.
- Toute modification de schéma passe par la V4 et est recopiée entièrement dans donneesStats.sql dans le même commit ; sur une base gérée par Flyway, seules les parties 3 et 6 de ce fichier sont exécutées (constitution I).
- Les données fictives (passes, stats détaillées) sont toujours signalées, dans les réponses, les rapports et les onglets existants (constitution VI).
- Aucun appel à un service externe (clarification Q3).
