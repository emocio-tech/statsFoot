# CLAUDE.md

## Projet

- **Utilisateur** : un utilisateur lambda qui veut des informations sur la Coupe du Monde 2026.
- **Tâche** : avoir des informations sur les joueurs et les équipes de la Coupe du Monde 2026, les classer et les comparer entre eux.
- **Preuve du vendredi** :
  - un comparatif des équipes et des rôles, basé sur des stats choisies par l'utilisateur ;
  - une fiche par joueur / équipe permettant de visualiser ses stats.

### Dans le périmètre

- Stats des équipes et des joueurs sur la saison de la Coupe du Monde 2026.
- Comparaison de joueurs de différentes équipes du même championnat.

### Hors périmètre

- Stats des équipes et des joueurs sur les autres championnats et sur les autres années de la Coupe du Monde.
- Comparaison inter-championnats et inter-années, et comparaison avec d'autres championnats.


## Commandes

Projet Maven multi-module à la **racine** (`pom.xml` parent, modules `frontend/` et `backend/`) ; le wrapper `mvnw` évite d'installer Maven.

### BackEnd (Maven)

```
./mvnw -pl backend test                                                    # lancer les tests
./mvnw -pl backend spring-boot:run -Dspring-boot.run.profiles=embedded     # PostgreSQL embarqué, données importées (http://localhost:8080)
```

Sans le profil `embedded`, l'application se connecte au PostgreSQL de `compose.yaml` (`docker compose up -d`). Le schéma vient de Flyway (`backend/src/main/resources/db/migration`). Les tests de recherche et de comparaison (`stats_foot.stats_foot.*`) démarrent un PostgreSQL embarqué rempli comme le profil `embedded` (`@FullDataTest`).

### FrontEnd (Node)

```
cd frontend
npm install              # installer les dépendances (première fois)
npm start                # lancer l'application Angular (http://localhost:4200, proxy /api vers :8080)
```

Le frontend de la branche n'a pas de runner de tests : les fichiers `*.spec.ts` venus de `main` (`chat/`, `player/`, `team/`) sont conservés mais exclus du build.

## Architecture

- **BackEnd** : Java avec la surcouche Spring Boot 3.5 (Spring AI 1.1), une seule racine de packages, `com.emocio.analysefoot` (onglet Analyse, rapports, notes, import ; sous-packages `player` et `team` pour la recherche et la comparaison, utilisées comme tools du LLM). Un seul contrôleur LLM : `AnalyseController`.
- **FrontEnd** : Angular.
- **Données** : base **PostgreSQL** gérée par Flyway, alimentée par l'import de `worldcup-full.json` puis `donneesStats.sql` (notes et stats fictives). `data/donneesStats.json` est un instantané de référence, utilisé uniquement par les tests.
- **MCP** : un seul serveur (transport **STREAMABLE**, `/mcp`) expose les outils de la branche (`metrics`, `rank`, `find`, `player_report`, `team_report`, …) et ceux de `main` (`searchPlayers`, `comparePlayers`). `findPlayers` et `compareTeams` sont réservés au LLM interne.

## Conventions

- **Langue du projet : l'anglais** pour tout ce qui fait partie du produit (classes, méthodes, variables, IHM, documentation, commentaires, messages d'erreur, etc.). Les échanges avec Claude restent en français.
- **BackEnd Java** : respecter les conventions de nommage Java usuelles :
  - classes et interfaces en `PascalCase`, méthodes et variables en `camelCase`, constantes en `UPPER_SNAKE_CASE`, packages en minuscules ;

## Pièges connus

- **Java 25** : le `pom.xml` parent vise Java 25. Avec un JDK 21 local, ajouter `-Djava.version=21` aux commandes Maven.
- **Migrations Flyway** : une migration appliquée ne se modifie pas (hook `protect-migrations.sh`) ; en ajouter une nouvelle (`V5__player_team_search.sql` apporte `team_fifa_code` et deux colonnes à `v_player_tournament_stats`).
- **Vues du tournoi** : les `SUM` de PostgreSQL renvoient parfois du `numeric` là où une entité attend un `Long` ; ces colonnes portent `@JdbcTypeCode(SqlTypes.NUMERIC)` (validation Hibernate `validate`).
- **Anciens fichiers `data/players.json` et `data/teams.json`** : plus utilisés par le code, laissés dans le dépôt.
- **Tests** : `@FullDataTest` démarre un PostgreSQL embarqué (port aléatoire) : le premier test est long (~30 s), le contexte est ensuite partagé.

## Git

### Branches

- Pour chaque fonctionnalité : `fonc/<nom_de_la_fonctionnalité>`
- Pour chaque skill : `skill/<nom_du_skill>`
- Pour chaque hook : `hook/<nom_du_hook>`

### Push vers main

Avant de pousser du code sur `main`, faire un merge ou un rebase de `main` en amont sur notre branche locale.

## Skills

### Skills prévus

- Génération de tests unitaires
- Génération de tests de non-régression
- Génération de la documentation
- Génération des IHM
- Génération d'une logique BackEnd
- Un ou plusieurs skills de Spec Driven Development (voir ci-dessous)

### sdd

Skill de Spec Driven Development (`.claude/skills/sdd/`). La spec est la source de vérité : il produit `specs/<feature>/spec.md` (quoi / pourquoi), `plan.md` (comment) puis `tasks.md` (tâches vérifiables), avec une validation humaine bloquante après chacun, avant d'implémenter puis de vérifier la conformité du code à la spec. Les fichiers de `specs/` sont rédigés en anglais. Il délègue aux agents `explorateur`, `analyse-impact`, `redacteur-plan-test`, `implementeur` et `relecteur`.

### feature-orchestrator

Skill d'orchestration d'une feature de bout en bout (discussion, exploration, plan, validation humaine, implémentation, revue, doc, commit). Il délègue aux agents `explorateur`, `analyse-impact`, `redacteur-plan-test`, `implementeur`, `relecteur` et `relecteur-doc`, avec deux validations humaines bloquantes (avant l'implémentation et avant le commit).

## Hooks

### Hooks prévus

- **Qualité de code** : bonnes pratiques, etc. Interdit le commit et le push si la qualité est inférieure à XX %.
- **Tests** : interdit le commit et le push si les tests ne sont pas OK.
