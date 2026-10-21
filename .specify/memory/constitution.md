<!--
Sync Impact Report
- Version change: 1.0.0 → 1.1.0 (MINOR : deux principes ajoutés, périmètre et règles de données
  élargis d'après backend/src/main/resources/db/migration/cadrage.txt)
- Modified principles:
  - I. Schéma versionné par Flyway → I. Schéma versionné par Flyway et recopié dans
    donneesStats.sql (référence : branche initDonneesNotation au lieu de main ; synchronisation
    obligatoire avec donneesStats.sql ; place de la partie 6)
  - II. Notation reproductible et explicable : inchangé sur le fond ; ajout du déterminisme des
    stats fictives
  - III. Tests sur une vraie base PostgreSQL : ajout des critères d'analyse et des vues
  - IV. L'API REST est le seul contrat front/back : ajout de l'indicateur « données fictives »
  - V. Simplicité et livrable unique : ajout de la branche unique et du SDK Anthropic comme seule
    dépendance externe prévue
- Added principles:
  - VI. Données réelles, calculées ou fictives : toujours distinguées
  - VII. Les chiffres viennent du SQL, jamais du modèle de langage
- Added sections: aucune (Contraintes techniques et Flux de développement complétés)
- Removed sections: aucune
- Templates requiring updates:
  - .specify/templates/plan-template.md : ✅ aucune modification (le « Constitution Check » lit
    cette constitution à l'exécution)
  - .specify/templates/spec-template.md : ✅ aucune modification
  - .specify/templates/tasks-template.md : ✅ aucune modification
- Follow-up TODOs:
  - README : la constitution (IV) exige d'y lister toutes les routes ; GET /api/stats/players et
    GET /api/stats/teams n'y figurent pas encore.
-->

# Analyse Foot (statsFoot) Constitution

## Core Principles

### I. Schéma versionné par Flyway et recopié dans donneesStats.sql (NON NÉGOCIABLE)

- Toute modification du schéma PostgreSQL DOIT passer par une nouvelle migration
  `backend/src/main/resources/db/migration/V{n}__{description}.sql`.
- Une migration déjà appliquée (présente sur `initDonneesNotation` : V1, V2, V3) NE DOIT JAMAIS
  être modifiée ni supprimée ; une correction se fait par une migration suivante.
- Hibernate reste en `ddl-auto: validate` : les entités JPA DOIVENT correspondre au schéma
  migré, jamais l'inverse.
- `donneesStats.sql` (même dossier) est la synthèse du modèle et le script de données de
  référence. Toute nouvelle migration DOIT y être recopiée dans le même commit, à la suite des
  parties existantes ; la partie 6 (stats fictives) DOIT rester la dernière, car elle lit les
  notes.
- Hors Flyway, seul `donneesStats.sql` (ou le skill `notation-matchs`) PEUT écrire en base, et
  uniquement des données (notes, stats fictives), jamais du schéma sur une base gérée par Flyway.
  Aucune table, vue ou colonne NE DOIT être créée à la main.

Justification : la base est reconstruite au démarrage sur chaque poste et dans les tests ; seul
l'historique Flyway garantit un schéma identique partout, et `donneesStats.sql` doit rester une
image fidèle de ce schéma pour servir de référence de données.

### II. Notation reproductible et explicable

- Les notes de match (joueurs et équipes) sont sur une échelle de 0 à 10, arrondies à 0,1.
- Le calcul DOIT être déterministe et idempotent : il efface et recalcule toutes les notes dans
  une seule transaction, et deux exécutions sur les mêmes données donnent le même résultat.
- Chaque note DOIT être accompagnée des colonnes de détail qui permettent d'expliquer sa valeur.
- Le barème est versionné (`v1`, `v2`, …) ; toute modification d'un coefficient ou d'une règle
  DOIT changer sa version et être documentée dans le skill `notation-matchs`.
- La génération des stats fictives (partie 6) suit les mêmes règles : déterministe (graine
  explicite), relançable, et cohérente avec les matchs réels.
- Après un recalcul, les contrôles de cohérence (nombre de notes attendu, moyenne proche de la
  note neutre) DOIVENT être exécutés avant d'annoncer un résultat.

Justification : une note ou un classement n'a de valeur que si on peut le recalculer et le
justifier.

### III. Tests sur une vraie base PostgreSQL

- Toute fonctionnalité touchant l'import, la notation, les vues statistiques, les critères
  d'analyse ou l'API DOIT être couverte par des tests backend (`./mvnw -pl backend test`).
- Les tests d'intégration s'exécutent contre une base PostgreSQL dédiée (`analysefoot_test`),
  vidée à chaque test ; les bases en mémoire ou les mocks du SGBD NE DOIVENT PAS remplacer ces
  tests pour le SQL, les vues et les migrations.
- Les jeux de données de test DOIVENT être des extraits réduits et versionnés
  (`worldcup-sample.json`), pas les fichiers complets.
- Chaque critère d'analyse DOIT avoir un test qui vérifie son résultat et sa règle d'égalité.
- Un bug corrigé DOIT s'accompagner d'un test qui l'aurait détecté.

Justification : la logique métier vit en grande partie en SQL ; seul PostgreSQL la teste
fidèlement.

### IV. L'API REST est le seul contrat front/back

- Le backend expose ses données uniquement sous `/api/**` ; le frontend Angular NE DOIT accéder
  aux données que via ces routes, au travers des services de `frontend/src/app/core`.
- Les contrôleurs DOIVENT renvoyer des DTO (`dto/`), jamais des entités JPA.
- Toute réponse qui contient ou utilise des données fictives DOIT l'indiquer (champ dédié), pour
  que l'interface l'affiche.
- Toute route ajoutée ou modifiée DOIT être testée côté backend et listée dans le README.
- Un changement incompatible d'une route existante DOIT être fait en même temps côté front et
  côté back, dans le même lot de modifications.

Justification : le découplage par DTO permet de faire évoluer le schéma sans casser l'affichage.

### V. Simplicité, branche unique et livrable unique

- Tout le développement se fait sur la seule branche `initDonneesNotation`, par commits
  successifs ; aucune branche de fonctionnalité n'est créée.
- Le projet reste un monolithe Maven multi-module (`frontend` + `backend`) produisant un seul
  JAR exécutable qui sert le front et l'API.
- Toute nouvelle dépendance, nouveau module, service externe ou couche d'abstraction DOIT être
  justifié par un besoin présent (YAGNI) dans le plan de la fonctionnalité. Seule dépendance
  externe prévue à ce jour : le SDK Java Anthropic, pour le Tool de l'onglet Analyse.
- Le code suit les conventions déjà en place (paquets `controller`, `service`, `repository`,
  `model`, `dto`, `importer` ; `core`, `features`, `shared` côté Angular) et réutilise
  l'existant (vues du tournoi, `stats-table`, `rating-badge`) avant d'ajouter du neuf.

Justification : un projet d'analyse doit rester compréhensible, sur une seule ligne d'historique,
et lançable en une commande.

### VI. Données réelles, calculées ou fictives : toujours distinguées

- Chaque donnée relève d'une seule origine : réelle (`worldcup-full.json`), calculée (notes,
  partie 3) ou fictive (stats détaillées, partie 6). Cette origine DOIT être connue pour chaque
  colonne exposée.
- `NULL` signifie « donnée inconnue » et DOIT rester distinct de `0` (valeur mesurée nulle), en
  base, dans l'API et à l'écran (« – »).
- Tout critère, classement ou rapport qui utilise une donnée fictive, même seulement pour
  départager une égalité, DOIT l'afficher (mention « données fictives »).
- Une question sans donnée pour y répondre (ex. meilleur passeur) DOIT répondre « donnée non
  disponible », jamais une approximation silencieuse.
- Les stats fictives DOIVENT être remplacées dès qu'une source réelle existe, et la partie 6 ne
  plus être exécutée ensuite.

Justification : les stats détaillées sont inventées ; les présenter comme réelles fausserait
toute analyse.

### VII. Les chiffres viennent du SQL, jamais du modèle de langage

- Les critères d'analyse forment un catalogue fermé, défini une seule fois côté backend ; le
  Tool, le serveur MCP, l'API et l'interface DOIVENT tous s'appuyer sur ce catalogue.
- Chaque critère DOIT avoir une requête SQL sur les vues du tournoi et une règle d'égalité
  explicite ; les ex aequo DOIVENT être affichés, jamais cachés.
- Le modèle de langage (API Claude, Tool `rank(criterion, scope)`) NE DOIT servir qu'à traduire
  une question libre en critère du catalogue. Il NE DOIT produire aucune statistique.
- L'application DOIT fonctionner sans clé d'API (reconnaissance par mots-clés et suggestions).
  La clé reste côté serveur, en variable d'environnement, et n'est jamais commitée.
- Les serveurs MCP (statsfoot, PostgreSQL) sont en lecture seule.

Justification : un résultat doit être vérifiable en relançant sa requête ; un modèle de langage
peut inventer des chiffres.

## Contraintes techniques

- Backend : Java 25, Spring Boot 3.5, Spring Data JPA, accès SQL direct par `JdbcClient` pour
  les lectures statistiques, Flyway, PostgreSQL 17.
- Frontend : Angular 20, construit par Maven via `frontend-maven-plugin` (Node téléchargé
  localement, aucune installation globale requise pour le build).
- Build : wrapper Maven `./mvnw` uniquement ; `./mvnw clean package` DOIT produire
  `backend/target/analyse-foot.jar`.
- Données : `donneesStats.sql` en 6 parties (schéma, notes, calcul des notes, stats
  détaillées, vues du tournoi, stats fictives) ; lecture par les vues
  `v_player_tournament_stats` et `v_team_tournament_stats`, filtrées sur `competition_id`.
- Configuration : les accès base se surchargent par variables d'environnement (`DB_URL`,
  `DB_USERNAME`, `DB_PASSWORD`, `TEST_DB_*`), de même que la clé d'API Claude ; aucun secret
  réel NE DOIT être commité.
- Import : la source est au format openfootball ; l'import DOIT rester idempotent (ignoré si la
  compétition existe déjà).

## Flux de développement

- Les fonctionnalités suivent le flux Spec Kit : `/speckit-specify` → `/speckit-plan` →
  `/speckit-tasks` → `/speckit-implement`, sur la branche `initDonneesNotation` (pas de branche
  par fonctionnalité). Le plan DOIT contenir une vérification explicite de conformité à cette
  constitution.
- Le cadrage `backend/src/main/resources/db/migration/cadrage.txt` sert d'entrée aux
  spécifications de l'onglet Analyse ; ses « décisions à prendre » DOIVENT être tranchées
  (`/speckit-clarify`) avant le plan.
- Ordre de réalisation de l'onglet Analyse : migration V4 des vues, rapports joueur et équipe,
  catalogue et `POST /api/analyse` par mots-clés, onglet Analyse, Tool Claude, puis skill,
  hooks et MCP.
- Le hook `pre-commit` (`git config core.hooksPath .githooks`) lance les tests backend ; un
  commit NE DOIT PAS être fait avec des tests en échec. `--no-verify` n'est admis que
  ponctuellement et doit être justifié dans le message de commit.
- La documentation (README, skills, cadrage, spécifications) et les messages de commit sont
  rédigés en français.
- Le README DOIT être mis à jour dès qu'une commande, une route ou une variable de configuration
  change.

## Governance

- Cette constitution prévaut sur toute autre pratique du projet. En cas de conflit entre un
  plan, une tâche, un skill ou le cadrage et la constitution, la constitution s'applique.
- Amendement : toute modification passe par `/speckit-constitution`, avec un rapport d'impact,
  et fait l'objet d'un commit dédié.
- Versionnement sémantique : MAJEUR pour la suppression ou la redéfinition incompatible d'un
  principe ; MINEUR pour l'ajout d'un principe ou d'une section, ou un élargissement notable ;
  CORRECTIF pour les clarifications et la rédaction.
- Conformité : chaque plan (`/speckit-plan`) et chaque analyse (`/speckit-analyze`) DOIVENT
  vérifier le respect des principes ; toute dérogation DOIT être justifiée dans la section
  « Complexity Tracking » du plan.

**Version**: 1.1.0 | **Ratified**: 2026-10-20 | **Last Amended**: 2026-10-21
