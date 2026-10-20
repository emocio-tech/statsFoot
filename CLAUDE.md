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

Les dossiers `backend/` et `frontend/` sont ceux prévus pour la cible (ils n'existent pas encore).

### BackEnd (Maven)

Le BackEnd utilise Maven avec le wrapper (`mvnw` + `pom.xml`) : ne pas dépendre d'un Maven installé sur le poste.

```
cd backend
./mvnw spring-boot:run   # lancer l'application
./mvnw test              # lancer les tests
```

### FrontEnd (Node)

```
cd frontend
npm install              # installer les dépendances (première fois)
npm start                # lancer l'application Angular
```

## Architecture

- **BackEnd** : Java avec la surcouche Spring Boot.
- **FrontEnd** : Angular.
- **Données** : fichiers JSON stockés dans `data/`.

## Conventions

- **Langue du projet : l'anglais** pour tout ce qui fait partie du produit (classes, méthodes, variables, IHM, documentation, commentaires, messages d'erreur, etc.). Les échanges avec Claude restent en français.
- **BackEnd Java** : respecter les conventions de nommage Java usuelles :
  - classes et interfaces en `PascalCase`, méthodes et variables en `camelCase`, constantes en `UPPER_SNAKE_CASE`, packages en minuscules ;

## Pièges connus

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
