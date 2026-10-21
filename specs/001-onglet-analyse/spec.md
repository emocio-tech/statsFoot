# Feature Specification: Onglet Analyse

**Feature Branch**: `initDonneesNotation` (branche unique, constitution V ; dossier de spécification `001-onglet-analyse`)

**Created**: 2026-10-21

**Status**: Draft

**Input**: User description: "à partir du cadrage cadrage.txt" (backend/src/main/resources/db/migration/cadrage.txt, mis à jour le 21/10/2026)

## Clarifications

### Session 2026-10-21

- Q: Meilleur passeur, alors qu'aucune passe n'existe dans les données ? → A: générer des
  passes décisives fictives avec les autres statistiques fictives (FR-003, FR-003b).
- Q: Les gardiens comptent-ils pour le joueur le plus fair-play ? → A: non, ils sont exclus
  (FR-003, FR-003c).
- Q: Les questions libres sont-elles envoyées à l'API Claude ? → A: non, mots-clés seulement ;
  l'API Claude fera l'objet d'une spécification ultérieure (FR-012).
- Q: À quoi peuvent servir les statistiques fictives ? → A: au classement du meilleur passeur
  et aux rubriques des rapports, toujours avec la mention « données fictives » ; aucun
  départage des sept autres critères ne les utilise (FR-003, FR-005).
- Q: Comment désigner la meilleure équipe ? → A: le champion (vainqueur de la finale), puis
  les équipes par position finale (FR-003).
- Q: Les critères de fair-play utilisent-ils les points FIFA ? → A: oui, pour le joueur et
  pour l'équipe (jaune -1, deuxième jaune -3 au total, rouge direct -4) (FR-003).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Répondre aux questions proposées (Priority: P1)

Un visiteur ouvre l'onglet Analyse et voit huit questions proposées : meilleur buteur,
meilleur passeur, joueur le plus agressif, joueur le moins fair-play, meilleure équipe,
équipe qui a marqué le plus de buts, équipe la moins fair-play, joueur le plus fair-play.
Il clique sur l'une d'elles. Il obtient en titre le joueur ou l'équipe qui répond, avec la
valeur qui le justifie (par exemple « Kylian Mbappé, 10 buts »), une phrase qui explique le
critère appliqué et la façon dont les égalités sont départagées, les éventuels ex aequo, et
le classement des cinq premiers pour situer le résultat.

**Why this priority**: c'est le cœur de l'onglet ; à elle seule, cette histoire répond aux huit
questions du cadrage, sans saisie libre ni rapport détaillé.

**Independent Test**: cliquer successivement sur chacune des huit questions et comparer la
réponse, l'explication et le top 5 aux chiffres vérifiés du cadrage.

**Acceptance Scenarios**:

1. **Given** l'onglet Analyse ouvert, **When** le visiteur clique sur « Meilleur buteur »,
   **Then** la réponse est « Kylian Mbappé, 10 buts », suivie du critère « buts hors but contre
   son camp ; égalité départagée par le moins de minutes jouées » et d'un top 5 où Haaland
   (476 min) précède Bellingham (616 min).
2. **Given** l'onglet ouvert, **When** il clique sur « Équipe qui a marqué le plus de buts »,
   **Then** la réponse est la France (20 buts, +10), l'Angleterre (20 buts, +8) apparaissant
   juste derrière dans le top 5, et l'explication mentionne le départage par la différence
   de buts.
3. **Given** l'onglet ouvert, **When** il clique sur « Équipe la moins fair-play »,
   **Then** la réponse est l'Argentine (-16 points de fair-play).
4. **Given** l'onglet ouvert, **When** il clique sur « Meilleure équipe », **Then** la
   réponse est l'Espagne, championne (vainqueur de la finale), suivie dans le top 5 de
   l'Argentine (finaliste), de l'Angleterre (3e), de la France (4e) et du quart de finaliste
   le mieux noté.
5. **Given** l'onglet ouvert, **When** il clique sur « Meilleur passeur », **Then** il voit
   le joueur qui a le plus de passes décisives, avec la mention « données fictives » à côté du
   critère.
6. **Given** l'onglet ouvert, **When** il clique sur « Joueur le plus fair-play »,
   **Then** la réponse est Lionel Messi (740 minutes sans carton), les gardiens étant exclus
   de ce classement.
7. **Given** deux candidats encore à égalité après la règle de départage, **When** la réponse
   s'affiche, **Then** les deux sont présentés comme ex aequo, aucun n'est masqué.
8. **Given** une question qui ne repose que sur des données réelles ou calculées (toutes sauf
   « Meilleur passeur »), **When** la réponse s'affiche, **Then** aucune mention « données
   fictives » n'accompagne le critère.

---

### User Story 2 - Consulter le rapport complet du joueur ou de l'équipe (Priority: P2)

Sous la réponse, le visiteur trouve le rapport complet du joueur ou de l'équipe désigné.
Pour un joueur : identité et équipe, matchs joués et minutes, buts, penalties, buts décisifs,
cartons et fair-play, statistiques détaillées (fautes, duels, distance, homme du match),
note moyenne, note de chaque match avec accès au détail de ce match, meilleur match, et son
rang sur chaque critère. Pour une équipe : parcours match par match avec scores et notes,
points, buts marqués et encaissés, fair-play, note moyenne, statistiques détaillées, effectif
avec les statistiques de chaque joueur, meilleur buteur et meilleur joueur selon la note.
Le même rapport s'ouvre depuis les onglets Joueurs et Équipes existants, en cliquant sur une
ligne.

**Why this priority**: le cadrage demande un « rapport complet » ; il donne sa valeur à la
réponse, mais l'histoire 1 reste utile sans lui.

**Independent Test**: ouvrir le rapport de Kylian Mbappé et celui de l'Espagne, depuis
l'onglet Analyse puis depuis les onglets Joueurs et Équipes, et vérifier chaque rubrique.

**Acceptance Scenarios**:

1. **Given** la réponse « Meilleur buteur » affichée, **When** le visiteur regarde sous la
   réponse, **Then** il voit le rapport de Kylian Mbappé avec 10 buts, ses 8 matchs, sa note
   moyenne et la note de chacun de ses matchs.
2. **Given** l'onglet Joueurs, **When** le visiteur clique sur la ligne d'un joueur,
   **Then** le rapport de ce joueur s'ouvre, identique à celui de l'onglet Analyse.
3. **Given** le rapport de l'Espagne, **When** le visiteur parcourt les matchs,
   **Then** chacun des 8 matchs apparaît dans l'ordre chronologique, avec score et note, et
   donne accès au détail du match.
4. **Given** un rapport qui contient des statistiques fictives, **When** il s'affiche,
   **Then** ces statistiques sont signalées comme fictives.

---

### User Story 3 - Poser une question en texte libre (Priority: P3)

Le visiteur tape sa propre formulation dans la barre de recherche, par exemple « qui a pris
le plus de jaunes ? » ou « l'équipe la plus sale ». L'onglet reconnaît la question
correspondante parmi les questions disponibles et y répond comme dans l'histoire 1. Si la
question n'est pas reconnue, l'onglet liste les questions disponibles.

**Why this priority**: confort d'usage ; les suggestions cliquables suffisent pour répondre
aux huit questions.

**Independent Test**: saisir plusieurs reformulations de chacune des huit questions et une
question hors sujet, et vérifier la réponse ou la liste des questions disponibles.

**Acceptance Scenarios**:

1. **Given** la barre de recherche, **When** le visiteur tape « qui a marqué le plus ? »,
   **Then** la réponse est celle de « Meilleur buteur ».
2. **Given** la barre de recherche, **When** il tape « quel temps fera-t-il demain ? »,
   **Then** l'onglet indique que la question n'est pas reconnue et liste les huit questions
   disponibles.
3. **Given** la barre de recherche, **When** le visiteur tape une formulation contenant un
   mot-clé connu (« buteur », « passeur », « jaune », « fair-play »...), sans accents ni
   majuscules, **Then** la question est reconnue.

---

### Edge Cases

- Égalité parfaite après la règle de départage : tous les ex aequo sont affichés, et le top 5
  peut alors compter plus de cinq lignes.
- Question ajoutée plus tard au catalogue sans donnée pour y répondre : « donnée non
  disponible », jamais une approximation.
- Gardiens : exclus du classement du joueur le plus fair-play ; ils restent dans les autres
  classements de joueurs.
- Valeur inconnue dans un rapport (statistique détaillée absente) : affichée « – » et non 0.
- Joueur entré en jeu sans minute jouée (remplacement noté après la fin du match) : il compte
  dans les matchs joués, mais ne doit pas fausser les ratios par minute.
- Question ambiguë correspondant à deux questions (« le moins fair-play » sans préciser joueur
  ou équipe) : la question sur le joueur et celle sur l'équipe sont proposées, le visiteur
  choisit.
- Aucune donnée importée : l'onglet l'indique au lieu d'afficher un classement vide.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: L'onglet Analyse MUST proposer les huit questions du cadrage sous forme de
  suggestions cliquables.
- **FR-002**: Chaque réponse MUST comporter le joueur ou l'équipe désigné, la valeur qui le
  justifie, une phrase expliquant le critère et sa règle de départage, les ex aequo éventuels,
  et le classement des cinq premiers.
- **FR-003**: Les critères MUST être les suivants :
  - meilleur buteur : le plus de buts hors but contre son camp ; égalité départagée par le
    moins de minutes jouées ;
  - meilleur passeur : le plus de passes décisives (statistique fictive, mention « données
    fictives » obligatoire) ; égalité départagée par le moins de minutes jouées ;
  - joueur le plus agressif : le plus de cartons jaunes ; égalité départagée par le plus de
    jaunes par 90 minutes jouées ;
  - joueur le moins fair-play : le plus de points de fair-play FIFA perdus (jaune -1,
    deuxième jaune -3, rouge direct -4) ; égalité départagée par le moins de minutes jouées ;
  - meilleure équipe : le champion (vainqueur de la finale, tirs au but compris) ; les autres
    équipes sont classées par position finale (finaliste, 3e, 4e, puis dernier tour
    atteint) ; à position égale, meilleure note moyenne ;
  - équipe qui a marqué le plus de buts : le plus de buts ; égalité départagée par la
    meilleure différence de buts ;
  - équipe la moins fair-play : le plus de points de fair-play FIFA perdus ;
  - joueur le plus fair-play : le joueur de champ (gardiens exclus) sans aucun carton qui a
    joué le plus de minutes ; une égalité restante donne des ex aequo.
- **FR-003b**: Les passes décisives MUST être générées avec les autres statistiques fictives,
  de façon cohérente avec les matchs : au plus une par but réel, attribuée à un coéquipier du
  buteur présent sur le terrain au moment du but, jamais sur un penalty ni sur un but contre
  son camp.
- **FR-003c**: Chaque joueur MUST être identifiable comme gardien ou joueur de champ, pour
  appliquer l'exclusion des gardiens.
- **FR-004**: Quand une égalité subsiste après la règle de départage, tous les ex aequo MUST
  être affichés.
- **FR-005**: Toute réponse ou rapport qui utilise une statistique fictive, y compris pour un
  seul départage, MUST afficher la mention « données fictives ».
- **FR-005b**: Les onglets Joueurs et Équipes existants MUST signaler en permanence leurs
  colonnes fictives (fautes, coups francs, duels, corners, hors-jeu, distance, homme du match).
- **FR-006**: Une valeur inconnue MUST être affichée « – », jamais 0.
- **FR-007**: Sous chaque réponse, le rapport complet du joueur ou de l'équipe désigné MUST
  être affiché, avec les rubriques décrites dans l'histoire 2.
- **FR-008**: Le rapport d'un joueur ou d'une équipe MUST pouvoir s'ouvrir depuis les onglets
  Joueurs et Équipes.
- **FR-009**: Le rapport joueur MUST donner son rang sur chacun des critères de joueur.
- **FR-010**: La barre de recherche MUST reconnaître une question libre parmi les questions
  disponibles et y répondre comme à la suggestion correspondante.
- **FR-011**: Une question non reconnue MUST afficher la liste des questions disponibles.
- **FR-012**: La reconnaissance MUST se faire uniquement par mots-clés associés à chaque
  question, sans tenir compte des accents ni des majuscules. Aucune question n'est envoyée à un
  service externe.
- **FR-013**: Tous les chiffres affichés MUST provenir des données du tournoi.
- **FR-014**: Ajouter une nouvelle question MUST se faire en un seul endroit (le catalogue des
  critères), repris par les suggestions, la reconnaissance et les réponses.

### Key Entities *(include if feature involves data)*

- **Question (critère)** : une entrée du catalogue ; libellé, portée (joueur ou équipe), règle
  de classement, règle de départage, population concernée (tous les joueurs ou joueurs de
  champ), mots-clés de reconnaissance, indication d'usage de données fictives, disponibilité.
- **Passe décisive (fictive)** : rattachée à un but réel, à son buteur et au passeur.
- **Réponse** : la question reconnue, le ou les gagnants (ex aequo compris), la valeur
  justificative, l'explication du critère, le top 5, la mention « données fictives » le cas
  échéant.
- **Rapport joueur** : identité, équipe, totaux du tournoi, statistiques détaillées, notes par
  match, meilleur match, rangs sur les critères.
- **Rapport équipe** : parcours match par match, totaux du tournoi, statistiques détaillées,
  effectif et ses statistiques, meilleur buteur et meilleur joueur.
- **Origine d'une donnée** : réelle (feuilles de match), calculée (notes) ou fictive
  (statistiques détaillées).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Les huit questions proposées donnent chacune une réponse conforme aux chiffres
  vérifiés du cadrage (100 % des cas).
- **SC-002**: Un visiteur obtient la réponse à une question proposée en un seul clic et en
  moins de 2 secondes.
- **SC-003**: Pour chacune des huit questions, au moins 3 reformulations courantes en texte
  libre sont reconnues par les mots-clés.
- **SC-004**: 100 % des réponses, rapports et colonnes des onglets Joueurs et Équipes qui
  utilisent des statistiques fictives portent la mention « données fictives ».
- **SC-005**: Aucune réponse ne cache un ex aequo : chaque égalité non départagée affiche tous
  les candidats concernés.
- **SC-006**: Le rapport d'un joueur ou d'une équipe est accessible en un clic depuis l'onglet
  Analyse, l'onglet Joueurs et l'onglet Équipes.

## Assumptions

- Les données sont celles de donneesStats.sql sur la branche initDonneesNotation : feuilles de
  match réelles de la Coupe du monde 2026 (104 matchs), notes calculées, statistiques
  détaillées fictives. Seule la dernière compétition importée est analysée.
- Les statistiques fictives (fautes, coups francs, duels, corners, hors-jeu, distance, homme
  du match) peuvent servir de départage ou de rubrique de rapport, toujours avec la mention
  « données fictives » (constitution VI). Seul le meilleur passeur les utilise pour un
  classement, avec les passes décisives fictives ajoutées à cette génération ; aucun
  départage n'en utilise.
- Les gardiens sont repérés par la règle déjà utilisée pour les statistiques fictives :
  premier titulaire de la feuille de match.
- L'interprétation des questions libres par un modèle de langage (API Claude) est reportée
  à une spécification ultérieure ; cette fonctionnalité ne repose que sur les mots-clés.
- La meilleure équipe est le champion ; la note moyenne ne sert qu'à départager des équipes
  éliminées au même tour.
- Les deux critères de fair-play utilisent les points FIFA plutôt que le seul nombre de
  rouges ou de deuxièmes jaunes, trop rares (15 exclusions, 2 deuxièmes jaunes).
- Limite connue : un gardien entré en jeu en cours de match n'est pas repéré comme gardien
  s'il n'a été titulaire dans les buts d'aucun match.
- L'onglet est en lecture seule, accessible sans compte, comme les onglets existants.
- Le top 5 et les rapports réutilisent la présentation des onglets Joueurs et Équipes.
- Hors périmètre : comparaison entre plusieurs compétitions, saisie ou correction de données,
  accès pour des outils de développement (serveur MCP), automatisations du dépôt (skill,
  hooks) ; ils font l'objet d'étapes ultérieures du cadrage.
