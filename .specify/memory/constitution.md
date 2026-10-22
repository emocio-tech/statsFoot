<!--
Sync Impact Report
- Version change: 2.1.0 → 2.2.0 (MINEUR : le principe VII n'impose plus de reconnaissance par mots-clés ni de plafond
  d'appels par minute ; le LLM devient le point d'entrée de la recherche)
- Modified principles:
  - VII. Le LLM est consulté en priorité pour toute saisie qui n'est pas un nom de joueur ou d'équipe ; il n'y a plus
    de reconnaissance par mots-clés ni de tri préalable par un appel `rank` imposé. Le tool `rank(scope, metric,
    order)` devient un tool de lecture comme les autres, exécuté par l'application (classement en SQL), que le
    modèle appelle quand il le juge utile, avec `metrics`, `stat`, `matches`, `find`, `player_report`,
    `team_report` et `calculate`. Le catalogue des 8 questions ne sert plus qu'aux suggestions cliquables. Le
    plafond d'appels par minute est supprimé ; reste un plafond d'allers-retours par saisie. Sans clé ou si le
    modèle n'aboutit pas : recherche par nom et suggestions
- Added/removed sections: aucune
- Templates requiring updates: aucun modèle ; specs/002-tool-claude-rank alignée (amendement 2) ; README déjà à jour
- Follow-up TODOs: aucun
-->

<!--
Sync Impact Report (précédent)
- Version change: 2.0.1 → 2.1.0 (MINEUR : élargissement notable du principe VII ; le tool `rank` ne s'appuie plus
  sur le catalogue des 8 questions)
- Modified principles:
  - VII. Le tool `rank` devient `rank(scope, metric, order)` : le modèle choisit une statistique dans une liste fermée
    (`StatMetrics`, partagée avec le tool `stat`) et un sens de tri, ou NONE ; le classement est calculé en SQL
    (RANK(), ex aequo affichés). Le catalogue des 8 questions (`AnalyseCriterion`) reste la voie des mots-clés et
    des suggestions de l'interface ; le MCP accepte les deux formes. L'exigence « le Tool, le MCP, l'API et
    l'interface s'appuient tous sur le même catalogue fermé » devient « sur deux listes fermées : les questions du
    catalogue et les statistiques ». Les règles de départage propres aux 8 questions ne s'appliquent pas au
    classement générique (valeur seule, ex aequo affichés)
- Added/removed sections: aucune
- Templates requiring updates: aucun modèle ; specs/002-tool-claude-rank mise à jour (rank sans critères)
- Follow-up TODOs: aucun
-->

<!--
Sync Impact Report (précédent)
- Version change: 2.0.0 → 2.0.1 (PATCH : titre du principe VII aligné sur son contenu)
  (1.1.2 → 2.0.0, MAJEUR : redéfinition du principe VII, qui n'interdit plus au modèle de langage de rédiger
  une réponse contenant des chiffres, sous conditions)
- Modified principles:
  - VII. Titre « Les chiffres viennent du SQL ; le modèle de langage ne les produit pas » remplacé par « Les
    classements viennent du SQL ; le modèle de langage est borné et tracé » : le modèle peut produire chiffres
    et texte, mais jamais le classement du catalogue, et reste borné et tracé
  - VII. Les chiffres viennent du SQL : devient « ... ; le modèle de langage ne les produit pas ». Le tool `rank`
    garde son rôle (traduire une question en critère ; le modèle peut produire si nécessaire chiffres et texte
    affiché, le classement du catalogue venant toujours du SQL). Nouveau mode libre : pour une question
    hors catalogue, le modèle peut rédiger une courte réponse, mais uniquement à partir des résultats de tools de
    lecture exécutés par l'application (mêmes fonctions et même SQL que le serveur MCP), avec calculs permis si
    nécessaire, étiquetée « réponse libre du LLM », bornée en allers-retours et en appels par minute, et tracée (aller/retour visibles dans les journaux
    et l'interface)
- Added/removed sections: aucune
- Templates requiring updates: aucun modèle ; specs/002-tool-claude-rank alignée (plan, research, spec, tasks,
  contrats) ; README déjà mis à jour
- Follow-up TODOs: aucun
-->

<!--
Sync Impact Report (précédent)
- Version change: 1.1.0 → 1.1.2 (PATCH : clarifications, aucun principe ajouté ni retiré)
- Modified principles:
  - V. Simplicité : la dépendance externe de l'assistant est Spring AI (spring-ai-openai, ChatClient), qui
    remplace le SDK Anthropic ; client construit à la main, sans starter ni autoconfiguration
  - VII. Les chiffres viennent du SQL : « le modèle de langage » n'est plus limité à l'API Claude (modèle du
    projet, quel qu'il soit, proxy de l'équipe ou Amazon Bedrock) ; mêmes garanties
- Added/removed sections: aucune
- Templates requiring updates: aucun
- Follow-up TODOs: aucun
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
  externe prévue à ce jour : Spring AI (`spring-ai-openai`, `ChatClient`), pour le Tool de l'onglet Analyse ; le
  client y est construit à la main, sans starter, pour que l'assistant reste désactivable sans clé.
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

### VII. Les classements viennent du SQL ; le modèle de langage est borné et tracé

- Deux listes fermées, définies une seule fois côté backend, servent de base aux tools, au serveur MCP, à l'API et à
  l'interface : les questions du catalogue (`AnalyseCriterion`, utilisées par les suggestions cliquables) et les
  statistiques de joueur et d'équipe (`StatMetrics`, utilisées par les tools `rank` et `stat`).
- Chaque question du catalogue et chaque statistique DOIT avoir une requête SQL sur les vues du tournoi ; chaque
  question du catalogue DOIT avoir une règle d'égalité explicite ; pour un classement générique, la valeur seule
  départage. Dans tous les cas les ex aequo DOIVENT être affichés, jamais cachés.
- Le modèle de langage (Claude ou un autre modèle du projet) est consulté en priorité pour toute saisie qui n'est
  pas un nom de joueur ou d'équipe : il l'interprète librement et PEUT produire, si nécessaire, des chiffres et du
  texte affiché. Aucun classement ne vient de lui : il vient toujours du SQL, par le tool de lecture
  `rank(scope, metric, order)`.
- Le modèle rédige une réponse courte en texte, en appelant autant de fois que nécessaire les tools de lecture
  (`metrics`, `rank`, `stat`, `matches`, `find`, `player_report`, `team_report`, `calculate`), à ces conditions :
  - il ne s'appuie QUE sur les résultats de tools de lecture exécutés par l'application (mêmes fonctions et mêmes
    vues SQL que le serveur MCP) ; aucun SQL libre, aucune écriture ;
  - il NE DOIT citer que des nombres et des noms renvoyés par ces tools, ne rien inventer ; il PEUT faire des
    calculs à partir d'eux si nécessaire, et DOIT signaler les données fictives (principe VI) ;
  - la réponse DOIT être présentée comme « réponse libre du LLM », distincte d'un classement calculé, et
    jamais comme un résultat vérifié ;
  - le nombre d'allers-retours par saisie DOIT être plafonné ; aucun plafond d'appels par minute n'est imposé ;
  - chaque échange (LLM, tool, SQL, MCP) DOIT être tracé, dans les journaux et dans l'interface (la trace contient
    la question saisie ; elle est désactivable).
- L'application DOIT fonctionner sans clé d'API (recherche d'un joueur ou d'une équipe par son nom et suggestions
  cliquables ; l'assistant est alors indisponible). La clé reste côté serveur, en variable d'environnement, et n'est jamais commitée.
- Les serveurs MCP (statsfoot, PostgreSQL) sont en lecture seule.

Justification : un classement doit être vérifiable en relançant sa requête ; un modèle de
langage peut inventer des chiffres. L'assistant répond à toute question sur les données, au prix
d'une réponse non garantie : elle est donc bornée aux résultats de lecture, étiquetée et tracée.

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

**Version**: 2.2.0 | **Ratified**: 2026-10-20 | **Last Amended**: 2026-10-22
