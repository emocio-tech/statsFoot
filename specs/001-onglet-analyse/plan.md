# Implementation Plan: Onglet Analyse

**Branch**: `initDonneesNotation` (branche unique ; dossier de fonctionnalité `001-onglet-analyse`) | **Date**: 2026-10-21 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-onglet-analyse/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Ajouter un onglet Analyse qui répond aux huit questions du cadrage (suggestions cliquables et
texte libre reconnu par mots-clés), avec explication du critère, ex aequo, top 5 et rapport complet
du joueur ou de l'équipe, rapport aussi accessible depuis les onglets Joueurs et Équipes.
Approche : une migration V4 (indicateur gardien réel, table de passes décisives fictives, colonnes
ajoutées aux vues du tournoi, dont la position finale qui désigne le champion), recopiée dans
`donneesStats.sql` dont la partie 6 génère les passes ; seules les parties de données (3 et 6) de
ce fichier sont exécutées sur les bases gérées par Flyway ; les données fictives des onglets
Joueurs et Équipes existants sont désormais signalées ;
un catalogue fermé de critères côté backend qui classe en SQL avec `RANK()` ; cinq routes `GET` ;
trois écrans Angular réutilisant `stats-table` et `rating-badge`. Détails : [research.md](research.md).

## Technical Context

**Language/Version**: Java 25 (backend), TypeScript 5.9 / Angular 20 (frontend), SQL PostgreSQL 17

**Primary Dependencies**: Spring Boot 3.5 (web, JdbcClient, Data JPA, validation), Flyway ; Angular
(signals, router, HttpClient). Aucune nouvelle dépendance.

**Storage**: PostgreSQL 17 (`analysefoot`, tests `analysefoot_test`) ; schéma par Flyway V1 à V4 ;
données de référence par `backend/src/main/resources/db/migration/donneesStats.sql`

**Testing**: JUnit 5 + AssertJ ; MockMvc (`@WebMvcTest`) pour les contrôleurs ; tests d'intégration
`@SpringBootTest` sur `analysefoot_test` avec `worldcup-sample.json` puis `donneesStats.sql` complet

**Target Platform**: application web servie par un seul JAR (`backend/target/analyse-foot.jar`),
navigateur récent

**Project Type**: application web (monolithe Maven `frontend` + `backend`)

**Performance Goals**: réponse à une question en moins de 2 secondes (SC-002) ; au plus 3 requêtes
SQL par réponse

**Constraints**: aucune écriture depuis l'API ; aucun service externe (Q3) ; colonnes existantes
des vues inchangées (routes `/api/stats/*` compatibles) ; `NULL` ≠ `0`

**Scale/Scope**: 1 compétition, 104 matchs, 48 équipes, 1 039 joueurs ; 8 critères ; 3 écrans
(Analyse, rapport joueur, rapport équipe) ; 5 routes

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Vérification | Version initiale (selon /speckit-analyze) | Après correction (Phase 1 révisée) |
|----------|--------------|---------------|---------------|
| I. Flyway + donneesStats.sql | V4 nouvelle, V1–V3 intactes ; V4 recopiée **entièrement** dans `donneesStats.sql` (parties 4 et 5, rétro-remplissage de `is_goalkeeper` compris) ; passes générées en partie 6, toujours dernière ; sur les bases Flyway, seules les parties 3 et 6 (données) sont exécutées ; entités JPA alignées (`MatchLineup.isGoalkeeper`) | ❌ (analyse : DDL rejoué, rétro-remplissage non recopié) | ✅ |
| II. Reproductible | passes décisives tirées avec la graine `fictif-v1`, relançable ; notes inchangées (barème v1) ; contrôles de cohérence des notes après chaque recalcul | ❌ (analyse : contrôles absents de T008) | ✅ |
| III. Tests PostgreSQL | tests d'intégration sur `analysefoot_test` ; pour chaque critère, un test du vainqueur et un test de la règle de départage ; tests des règles de cohérence des passes et des gardiens ; parties de données de `donneesStats.sql` exécutées en test | ✅ | ✅ |
| IV. API = contrat | 5 routes `/api`, DTO dédiés, champ `fictitious` / `fictitiousFields` ; `fictitiousFields` ajouté aux lignes de `/api/stats/*` (ajout compatible) ; routes testées et ajoutées au README | ❌ (analyse : `/api/stats/*` sans champ dédié) | ✅ |
| V. Simplicité, branche unique | commits sur `initDonneesNotation` ; aucune dépendance ; réutilisation de `stats-table`, `rating-badge`, des vues et des paquets existants (nouveau paquet `analyse` dans la convention `controller/service/dto`) | ✅ | ✅ |
| VI. Origine des données | gardien et position finale = R, passes = F, fair-play joueur = C ; mention « données fictives » sur `TOP_ASSIST`, les stats détaillées des rapports et les colonnes fictives des onglets Joueurs et Équipes ; aucun départage fictif ; `assists` NULL si non générées | ❌ (analyse : onglets existants sans mention) | ✅ |
| Flux (cadrage) | décisions du cadrage tranchées avant le plan : passeur fictif, gardiens exclus, mots-clés seuls, stats fictives pour le passeur et les rapports, meilleure équipe = champion, fair-play en points FIFA (spec, Clarifications) | ❌ (analyse : décisions 2 et 3 prises par hypothèse) | ✅ |
| VII. Chiffres du SQL | catalogue fermé unique ; classement `RANK()` en SQL ; ex aequo affichés ; aucun modèle de langage (reporté) | ✅ | ✅ |

La première version de ce plan se déclarait conforme à tort ; `/speckit-analyze` a relevé cinq
écarts (colonne « Version initiale »), corrigés le 21/10/2026 dans la spécification, la recherche
(R3, R8, R10), le modèle de données et les tâches. Aucune dérogation : la section Complexity
Tracking est vide.

## Project Structure

### Documentation (this feature)

```text
specs/001-onglet-analyse/
├── spec.md
├── plan.md              # ce fichier
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   └── api.md           # Phase 1
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
backend/src/main/
├── java/com/emocio/analysefoot/
│   ├── analyse/                          # nouveau
│   │   ├── AnalyseCriterion.java         # catalogue fermé (enum)
│   │   ├── QuestionMatcher.java          # reconnaissance par mots-clés
│   │   └── AnalyseService.java           # classement RANK() sur les vues
│   ├── controller/
│   │   ├── AnalyseController.java        # /api/analyse, /api/analyse/questions, /api/analyse/{id}
│   │   └── ReportController.java         # /api/players/{id}/report, /api/teams/{id}/report
│   ├── dto/                              # AnalyseQuestion, AnalyseAnswer, RankedEntry, PlayerReport, TeamReport
│   │                                     # + fictitiousFields dans PlayerTournamentStats, TeamTournamentStats
│   ├── service/ReportQueryService.java   # rapports et rangs
│   ├── importer/WorldCupImporter.java    # renseigne is_goalkeeper (premier titulaire)
│   └── model/MatchLineup.java            # champ isGoalkeeper
└── resources/db/migration/
    ├── V4__analyse.sql                   # is_goalkeeper + rétro-remplissage, goal_assist, vues
    └── donneesStats.sql                  # parties 4, 5 et 6 mises à jour

backend/src/test/java/com/emocio/analysefoot/
├── analyse/QuestionMatcherTest.java
├── controller/AnalyseControllerTest.java
├── controller/ReportControllerTest.java
└── WorldCupImportAndRatingTest.java      # + donneesStats.sql complet, critères, passes, gardiens

frontend/src/app/
├── core/models/analyse.ts
├── core/services/analyse.service.ts
├── features/analyse/analyse.ts           # onglet Analyse
├── features/reports/player-report.ts     # /joueurs/:id et sous une réponse
├── features/reports/team-report.ts       # /equipes/:id et sous une réponse
├── features/players/player-stats.ts      # clic sur une ligne → rapport
├── features/teams/team-stats.ts          # clic sur une ligne → rapport
├── shared/stats-table.ts                 # sortie optionnelle rowClick, colonnes fictives marquées
├── app.routes.ts                         # /analyse, /joueurs/:id, /equipes/:id
└── app.ts                                # lien « Analyse » dans le menu

README.md                                 # nouvelles routes (+ /api/stats/* manquantes)
```

**Structure Decision**: structure web existante du dépôt (`backend/` Spring Boot, `frontend/`
Angular, un JAR). Seul ajout de paquet : `analyse/` pour le catalogue et la reconnaissance, qui ne
relèvent ni d'un contrôleur ni d'un service de requêtes générique.

## Ordre de réalisation (commits sur initDonneesNotation)

1. V4 + `donneesStats.sql` (parties 4, 5, 6) + import du gardien + tests de cohérence ;
   signalement des données fictives dans `/api/stats/*` et les onglets Joueurs et Équipes.
2. Rapports joueur et équipe (routes, écrans, clic depuis Joueurs et Équipes) – histoire P2.
3. Catalogue, classement et `GET /api/analyse/{id}`, suggestions de l'onglet – histoire P1.
4. Reconnaissance par mots-clés et `GET /api/analyse?q=` – histoire P3.
5. README et validation selon [quickstart.md](quickstart.md).

L'étape 2 précède l'étape 3 parce que la réponse affiche le rapport ; P1 reste démontrable seule
si le rapport est masqué.

## Complexity Tracking

Aucune violation de la constitution à justifier.
