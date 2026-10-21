# Phase 0 – Recherche : Onglet Analyse

Toutes les inconnues du contexte technique sont levées ci-dessous. Références : [spec.md](spec.md),
constitution v1.1.0, cadrage `backend/src/main/resources/db/migration/cadrage.txt`.

## R1. Repérer les gardiens (FR-003c)

- **Decision** : ajouter `match_lineup.is_goalkeeper` (migration V4). L'import le renseigne pour le
  premier titulaire de chaque composition de `worldcup-full.json` ; la V4 le rétro-remplit pour la
  base existante avec la règle SQL déjà utilisée par la partie 6 de `donneesStats.sql`.
- **Rationale** : l'ordre de la feuille de match place le gardien en premier ; c'est une donnée
  réelle, à stocker avec la composition. La règle SQL a été comparée au JSON le 21/10/2026 :
  elle retrouve les mêmes gardiens, y compris les 13 rotations (10 équipes).
- **Alternatives considered** : garder la règle seulement dans une vue (fragile, recalculée à chaque
  lecture, et déjà dupliquée dans la partie 6) ; ajouter une colonne `position` à `player` (le poste
  des joueurs de champ est inconnu, et un gardien peut changer selon le match).

## R2. Passes décisives fictives (FR-003b)

- **Decision** : nouvelle table `goal_assist (goal_id PK → goal, player_id → player)` créée par la
  V4 et remplie par la partie 6 de `donneesStats.sql`. Pour chaque but qui n'est ni un penalty ni
  un but contre son camp, un tirage déterministe (graine `fictif-v1`) décide s'il y a une passe
  décisive (probabilité 0,75), puis choisit le passeur parmi les coéquipiers présents sur le
  terrain à la minute du but, hors buteur, avec un poids propre à chaque joueur (profil
  « créateur » stable) et un poids réduit pour le gardien.
- **Rationale** : rattacher la passe à un but garantit les règles de cohérence de FR-003b (au plus
  une par but, coéquipier sur le terrain, jamais sur penalty ni contre son camp). Le total par
  joueur s'obtient par la vue. La probabilité de 0,75 donne un ordre de grandeur réaliste.
- **Alternatives considered** : une colonne `assists` dans `player_match_stats` répartie comme les
  autres stats (ne garantit pas le lien avec un but réel) ; ne pas générer et répondre « donnée non
  disponible » (écarté par la clarification Q1).
- **Présence sur le terrain** : la partie 6 recalcule les intervalles de présence (entrée : coup
  d'envoi ou remplacement ; sortie : remplacement ou exclusion), avec le même codage
  `minute × 100 + temps additionnel` que la partie 3, sans dépendre des tables temporaires de la
  partie 3.

## R3. Compléments des vues du tournoi (V4)

- **Decision** : la V4 remplace les deux vues avec `CREATE OR REPLACE VIEW`, en ajoutant les
  nouvelles colonnes **à la fin** (PostgreSQL interdit de modifier l'ordre ou le type des colonnes
  existantes d'une vue) :
  - `v_player_tournament_stats` : `assists`, `second_yellows`, `direct_reds`, `fair_play_points`,
    `decisive_goals`, `is_goalkeeper` ;
  - `v_team_tournament_stats` : `stage_reached` et `final_position` (1 champion … 8 phase de
    groupes), pour désigner la meilleure équipe (clarification du 21/10/2026).
- **Rationale** : les critères de la spécification ont besoin de ces colonnes ; les routes et DTO
  existants (`/api/stats/*`) restent compatibles puisque les colonnes existantes ne bougent pas.
- **Alternatives considered** : calculer ces valeurs dans les requêtes Java (duplique la logique
  hors de la source unique de données) ; désigner la meilleure équipe par la note moyenne
  (écarté : la meilleure équipe est le champion).
- **Règles** : `fair_play_points` joueur = somme de jaune -1, deuxième jaune -2 (soit -3 avec le
  premier jaune), rouge direct -4, comme pour l'équipe ; `is_goalkeeper` = gardien d'au moins un
  match ; `assists` = NULL si aucune passe décisive n'a été générée dans la base (donnée inconnue),
  sinon le nombre (0 compris).

## R4. Catalogue des critères et classement SQL

- **Decision** : une énumération Java `AnalyseCriterion` (backend, paquet `analyse`) porte, pour
  chaque question : identifiant, libellé, portée (`PLAYER`/`TEAM`), population (tous les joueurs ou
  joueurs de champ), mots-clés, expression de valeur affichée, clés de tri principal puis de
  départage, indicateur « données fictives », phrase d'explication. Un service unique construit la
  requête de classement sur la vue du tournoi, avec `RANK() OVER (ORDER BY <tri>, <départage>)` :
  les gagnants sont le rang 1, le top 5 est `rang <= 5` (il peut donc compter plus de 5 lignes en
  cas d'ex aequo, comme le demandent les cas limites).
- **Rationale** : une seule source de vérité (FR-014) ; `RANK()` traite naturellement les ex aequo ;
  les expressions SQL viennent d'une liste fermée codée en dur, jamais de l'utilisateur (pas
  d'injection possible).
- **Alternatives considered** : une table SQL de critères (complexité sans besoin) ; une requête
  écrite à la main par critère (duplication, et FR-014 non respecté).
- **Ratios par 90 minutes** : calculés dans l'expression de départage avec `NULLIF(minutes, 0)` ;
  un joueur à 0 minute a un ratio NULL, rangé en dernier (`NULLS LAST`).

## R5. Reconnaissance des questions par mots-clés (FR-010 à FR-012)

- **Decision** : normaliser la question (minuscules, accents retirés avec
  `java.text.Normalizer`, ponctuation remplacée par des espaces), puis compter pour chaque critère
  les mots-clés présents. Les mots « joueur » et « équipe » (et leurs variantes) orientent vers la
  portée correspondante. Résultat : un critère reconnu (meilleur score unique), une ambiguïté
  (plusieurs critères à égalité de score, par exemple « le moins fair-play » sans portée), ou
  aucune reconnaissance (score nul).
- **Rationale** : simple, déterministe, testable, sans service externe (clarification Q3).
- **Alternatives considered** : distance d'édition ou recherche plein texte PostgreSQL (inutile pour
  8 questions) ; modèle de langage (reporté à une spécification ultérieure).

## R6. Contrat d'API

- **Decision** : cinq routes en lecture, toutes en `GET` (aucune écriture) :
  `GET /api/analyse/questions`, `GET /api/analyse?q=...`, `GET /api/analyse/{questionId}`,
  `GET /api/players/{id}/report`, `GET /api/teams/{id}/report`. Détails dans
  [contracts/api.md](contracts/api.md).
- **Rationale** : la recherche ne modifie rien ; `GET` rend les réponses partageables par URL et
  cohérentes avec les routes existantes. Le cadrage prévoyait `POST /api/analyse` en vue d'un corps
  de requête pour le modèle de langage, désormais hors périmètre.
- **Alternatives considered** : `POST /api/analyse { question }` (non idempotent en apparence, sans
  bénéfice ici).

## R7. Rapports joueur et équipe, réutilisés par les onglets existants

- **Decision** : deux composants Angular autonomes (`player-report`, `team-report`), affichés sous la
  réponse de l'onglet Analyse et sur deux nouvelles routes `/joueurs/:id` et `/equipes/:id`. Le
  composant partagé `stats-table` reçoit une sortie optionnelle de clic sur une ligne, utilisée par
  les onglets Joueurs et Équipes pour ouvrir ces routes.
- **Rationale** : FR-007, FR-008 et SC-006 ; réutiliser l'existant (constitution V).
- **Alternatives considered** : une fenêtre modale (moins partageable) ; dupliquer les rapports dans
  chaque onglet.

## R8. Tests et synchronisation avec donneesStats.sql

- **Decision** : sur une base gérée par Flyway (tests et base `analysefoot`), seules les parties de
  **données** de `donneesStats.sql` sont exécutées : la partie 3 (notes, qui ouvre la transaction)
  et la partie 6 (stats fictives et passes décisives, qui la ferme), découpées par les marqueurs
  `-- PARTIE 3`, `-- PARTIE 4` et `-- PARTIE 6`. Les parties 1, 2, 4 et 5 (schéma) ne sont jamais
  exécutées sur ces bases (constitution I, choix du 21/10/2026). Les méta-commandes psql sont
  retirées, comme pour `compute_ratings.sql`. Hors application, un script PowerShell/bash
  documenté dans le quickstart fait le même découpage pour la base de développement.
- **Rationale** : respecte la constitution I à la lettre ; le schéma reste vérifié par Flyway, et
  les tests vérifient que les parties de données restent exécutables sur le schéma migré.
- **Alternatives considered** : exécuter tout le fichier (DDL sans effet, mais interdit par la
  constitution I) ; extraire la partie 6 dans un script séparé (deux sources à tenir à jour) ;
  insérer des stats fictives à la main dans les tests (ne teste pas la génération).
- **Alignement du schéma recopié** : un test vérifie que l'en-tête de `donneesStats.sql` cite chaque
  migration `V{n}__*.sql` présente dans le dossier ; la conformité du contenu reste vérifiée en
  revue (constitution I).
- **Contrôle** : la fonction temporaire `pg_temp.rnd` a un corps sans point-virgule, compatible avec
  le découpage par `ScriptUtils`.

## R10. Signaler les données fictives des onglets existants

- **Decision** : ajouter `fictitiousFields` (liste constante) aux DTO de `/api/stats/players` et
  `/api/stats/teams` ; côté Angular, `StatsColumn` gagne un indicateur `fictitious` : les en-têtes
  concernés portent un astérisque et l'infobulle « donnée fictive », et une légende permanente
  « * données fictives » remplace le bandeau actuel (qui n'apparaissait qu'en l'absence de données).
- **Rationale** : constitution IV (champ dédié) et VI (mention à l'écran), FR-005b ; ajout
  compatible, les champs existants ne changent pas.
- **Alternatives considered** : un en-tête HTTP (invisible pour l'interface) ; changer la réponse en
  objet `{ rows, fictitiousFields }` (rupture de contrat sans bénéfice).

## R9. Performance

- **Decision** : aucune mise en cache. Les vues portent sur 104 matchs et 1 039 joueurs ; une
  réponse demande au plus 3 requêtes (classement, rapport, rangs).
- **Rationale** : SC-002 (moins de 2 secondes) est largement atteint avec des requêtes de quelques
  millisecondes sur ce volume.
- **Alternatives considered** : vues matérialisées (rafraîchissement à gérer après chaque calcul,
  sans besoin mesuré).
