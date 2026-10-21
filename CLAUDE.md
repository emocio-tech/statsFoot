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

Le BackEnd (Maven) est à la **racine** du dépôt (`pom.xml`, `mvnw`, `src/`) ; le FrontEnd Angular est dans `frontend/`.

### BackEnd (Maven)

Le BackEnd utilise Maven avec le wrapper (`mvnw` + `pom.xml`) : ne pas dépendre d'un Maven installé sur le poste.

```
./mvnw spring-boot:run   # lancer l'application (http://localhost:8080)
./mvnw test              # lancer les tests
```

Au premier démarrage, l'application crée la base H2 `data/h2/` et l'alimente depuis `data/donneesStats.json` (voir « Pièges connus »). Les tests utilisent une base H2 en mémoire.

### FrontEnd (Node)

```
cd frontend
npm install              # installer les dépendances (première fois)
npm start                # lancer l'application Angular (http://localhost:4200, proxy /api vers :8080)
npm test                 # lancer les tests (non interactif)
```

## Architecture

- **BackEnd** : Java avec la surcouche Spring Boot.
- **FrontEnd** : Angular.
- **Données** : base **H2** (fichier `data/h2/`, non versionné) alimentée au premier démarrage depuis `data/donneesStats.json` ; le schéma suit `data/donneesStats.sql` (référence PostgreSQL) porté dans `src/main/resources/db/schema.sql`.

## Conventions

- **Langue du projet : l'anglais** pour tout ce qui fait partie du produit (classes, méthodes, variables, IHM, documentation, commentaires, messages d'erreur, etc.). Les échanges avec Claude restent en français.
- **BackEnd Java** : respecter les conventions de nommage Java usuelles :
  - classes et interfaces en `PascalCase`, méthodes et variables en `camelCase`, constantes en `UPPER_SNAKE_CASE`, packages en minuscules ;

## Pièges connus

- **Base H2** : `data/h2/` est ignoré par git et créé au premier démarrage. Pour recharger les données, arrêter l'application et supprimer ce dossier. H2 refuse un chemin relatif sans `./` (`statsfoot.database-file=./data/h2/statsfoot`).
- **`data/donneesStats.sql` n'est pas exécuté** : c'est du PostgreSQL sans données (commandes psql, `LATERAL`...). Les données viennent de `data/donneesStats.json` ; le schéma H2 est dans `src/main/resources/db/schema.sql` (écarts documentés en tête du fichier).
- **Anciens fichiers `data/players.json` et `data/teams.json`** : plus utilisés par le code, laissés dans le dépôt.
- **Tests** : ils n'écrivent jamais dans `data/h2/` (base en mémoire, `src/test/resources/application.properties`).

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
