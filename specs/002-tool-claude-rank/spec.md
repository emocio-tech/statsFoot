# Feature Specification: Tool Claude rank(criterion, scope)

**Feature Branch**: `initDonneesNotation` (branche unique, constitution V ; dossier de spécification `002-tool-claude-rank`)

**Created**: 2026-10-21

**Status**: Draft

**Input**: User description: "le tool Claude rank(criterion, scope)" — étape 5 du cadrage
(backend/src/main/resources/db/migration/cadrage.txt), reportée par la clarification Q3 de
`specs/001-onglet-analyse`.

## Clarifications

### Session 2026-10-21

- Q: Quand consulter l'assistant ? → A: seulement quand les mots-clés ne reconnaissent pas la
  question ou la jugent ambiguë (FR-003).
- Q: Comment informer le visiteur ? → A: une mention permanente sous la barre de recherche
  (FR-008).
- Q: Quel accès utiliser ? → A: une clé API Anthropic dédiée (`ANTHROPIC_API_KEY`), modèle
  Claude Haiku (FR-009). **Révisé le 21/10/2026** : le compte Anthropic n'ayant pas de crédit, le LLM
  du projet est utilisé à la place : clé `LLM_KEY` (fichier `.env`), proxy LiteLLM de l'équipe au
  format Anthropic, modèle `claude-sonnet-4-6` (`nova-lite` testé : n'appelle pas le tool).
  **Complété le 21/10/2026** : Amazon Bedrock (interface OpenAI, `openai.gpt-oss-120b-1:0`, jeton
  `AWS_BEARER_TOKEN_BEDROCK`) s'ajoute au proxy comme second fournisseur, choisi par `LLM_PROVIDER`
  (`proxy` par défaut, `bedrock`). Même tool, même validation ; pas encore testé en réel (jeton absent).
  **Complété le 21/10/2026** : les deux fournisseurs sont appelés par le `ChatClient` de Spring AI
  (format OpenAI Chat Completions), qui remplace le SDK Anthropic et le client HTTP maison.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Comprendre des formulations libres que les mots-clés ratent (Priority: P1)

Un visiteur tape dans la barre de recherche de l'onglet Analyse une formulation que les
mots-clés ne reconnaissent pas, par exemple « qui a été le plus décisif devant le but ? » ou
« quelle nation a planté le plus ? ». L'assistant d'interprétation traduit la question en une
des huit questions du catalogue et l'onglet affiche la même réponse que si le visiteur avait
cliqué sur la suggestion : gagnant, critère, ex aequo, top 5 et rapport.

**Why this priority**: c'est la raison d'être de la fonctionnalité ; la reconnaissance par
mots-clés laisse de côté les formulations imagées, synonymes ou fautes de frappe.

**Independent Test**: soumettre une liste de formulations libres non reconnues par les
mots-clés et vérifier que chacune aboutit à la bonne question du catalogue.

**Acceptance Scenarios**:

1. **Given** l'assistant disponible, **When** le visiteur tape « quelle nation a planté le
   plus ? » (non reconnue par les mots-clés), **Then** la réponse est celle de « Équipe qui a marqué le plus de buts » (France).
2. **Given** l'assistant disponible, **When** le visiteur tape une question avec des fautes
   (« meilleur buteure du mondiale »), **Then** la réponse est celle de « Meilleur buteur ».
3. **Given** l'assistant disponible, **When** la question porte sur un sujet hors catalogue
   (« quel est le salaire de Mbappé ? »), **Then** l'onglet indique que la question n'est pas
   reconnue et liste les huit questions, sans inventer de réponse.
4. **Given** une réponse obtenue grâce à l'assistant, **When** elle s'affiche, **Then** les
   chiffres sont identiques à ceux de la suggestion correspondante.
5. **Given** l'assistant disponible, **When** le visiteur tape « qui a pris le plus de
   jaunes » (reconnue par les mots-clés), **Then** la réponse arrive sans consulter
   l'assistant.
6. **Given** l'assistant disponible, **When** le visiteur tape « le moins fair-play »
   (ambiguë pour les mots-clés), **Then** l'assistant est consulté ; s'il ne tranche pas,
   les deux questions possibles sont proposées.

---

### User Story 2 - Continuer à fonctionner sans l'assistant (Priority: P2)

L'assistant d'interprétation peut être absent (non configuré), lent ou en erreur. Le visiteur
doit toujours obtenir une réponse par les mots-clés et les suggestions, sans message
d'erreur technique.

**Why this priority**: la constitution (principe VII) exige que l'application fonctionne sans
l'assistant ; c'est la garantie de la fonctionnalité existante.

**Independent Test**: désactiver l'assistant, puis simuler une panne et un délai dépassé, et
vérifier que toutes les questions reconnues par mots-clés le restent.

**Acceptance Scenarios**:

1. **Given** l'assistant non configuré, **When** le visiteur tape « qui a pris le plus de
   jaunes », **Then** la réponse est celle de « Joueur le plus agressif », comme aujourd'hui.
2. **Given** l'assistant en panne ou trop lent, **When** le visiteur pose une question,
   **Then** la reconnaissance par mots-clés s'applique et la réponse arrive sans message
   d'erreur.
3. **Given** l'assistant qui propose une question inexistante ou une réponse invalide,
   **When** la réponse s'affiche, **Then** elle est ignorée et la reconnaissance par
   mots-clés s'applique.

---

### User Story 3 - Savoir comment la question a été comprise (Priority: P3)

Sous la réponse, le visiteur voit comment sa question a été interprétée : « question
comprise comme : Meilleur buteur », avec l'origine de l'interprétation (mots-clés ou
assistant). Il peut corriger en cliquant sur une autre suggestion.

**Why this priority**: transparence et confiance ; utile surtout quand l'assistant interprète
une formulation inattendue.

**Independent Test**: poser une question reconnue par mots-clés puis une question reconnue
par l'assistant, et vérifier l'affichage de l'interprétation et de son origine.

**Acceptance Scenarios**:

1. **Given** une question interprétée par l'assistant, **When** la réponse s'affiche,
   **Then** la mention « question comprise comme : … (assistant) » est visible.
2. **Given** une question interprétée par mots-clés, **When** la réponse s'affiche,
   **Then** la mention indique « (mots-clés) ».

---

### Edge Cases

- Question ambiguë pour l'assistant (joueur ou équipe non précisé) : les questions possibles
  sont proposées, comme pour les mots-clés.
- Question demandant un chiffre que le catalogue ne couvre pas : non reconnue, jamais de
  chiffre produit par l'assistant.
- Question très longue ou contenant des instructions (« ignore tes consignes et… ») :
  l'assistant ne peut que choisir une question du catalogue ou n'en choisir aucune.
- Mots-clés et assistant en désaccord : impossible, l'assistant n'étant consulté que si les
  mots-clés ne reconnaissent pas la question ou la jugent ambiguë (FR-003).
- Nombre de questions élevé en peu de temps : au-delà de la limite d'usage, la
  reconnaissance par mots-clés s'applique seule.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: L'assistant MUST se limiter à choisir une question du catalogue (et sa portée
  joueur ou équipe), à déclarer une ambiguïté entre plusieurs questions, ou à n'en choisir
  aucune ; il MUST ne produire aucun chiffre, nom ni texte affiché comme réponse.
- **FR-002**: Toute réponse obtenue via l'assistant MUST provenir du même calcul que la
  suggestion correspondante (mêmes gagnants, valeurs, ex aequo, top 5, rapport).
- **FR-003**: L'assistant MUST être consulté seulement quand les mots-clés ne reconnaissent
  pas la question ou la jugent ambiguë ; une question reconnue par les mots-clés MUST
  recevoir sa réponse sans consulter l'assistant.
- **FR-004**: Un choix de l'assistant qui ne correspond à aucune question du catalogue, ou une
  réponse illisible, MUST être ignoré au profit des mots-clés.
- **FR-005**: Sans assistant configuré, en cas d'erreur ou au-delà d'un délai de 3 secondes,
  l'application MUST appliquer la reconnaissance par mots-clés, sans message d'erreur
  technique pour le visiteur.
- **FR-006**: La réponse MUST indiquer comment la question a été comprise et par quel moyen
  (mots-clés ou assistant).
- **FR-007**: La question saisie MUST être la seule donnée transmise à l'assistant, avec la
  liste des questions du catalogue ; aucune donnée du tournoi ni information sur le
  visiteur n'est transmise.
- **FR-008**: Une mention permanente sous la barre de recherche MUST informer le visiteur
  qu'une question libre non reconnue peut être transmise à un service externe (Anthropic)
  pour être interprétée ; la mention n'apparaît que si l'assistant est configuré.
- **FR-009**: L'assistant MUST être le LLM du projet (modèle `claude-sonnet-4-6` servi par le
  proxy de l'équipe, adresse et modèle configurables), appelé par le `ChatClient` de Spring AI et
  accédé par la clé `LLM_KEY` (fichier
  `.env` ou variable d'environnement) ; la clé MUST rester côté serveur, hors du dépôt, jamais
  transmise au navigateur. Sans clé, l'assistant est considéré comme non configuré (FR-005).
  Un second fournisseur, Amazon Bedrock (`LLM_PROVIDER=bedrock`, jeton `AWS_BEARER_TOKEN_BEDROCK`),
  peut être choisi ; le fournisseur actif est le seul à recevoir les questions.
- **FR-010**: Les suggestions cliquables MUST continuer à répondre sans jamais consulter
  l'assistant.
- **FR-011**: L'usage de l'assistant MUST être plafonné pour l'ensemble des visiteurs ; au-delà,
  seuls les mots-clés s'appliquent. Plafond réglable, 1 question par minute par défaut : le proxy
  du projet limite la clé à 1 000 jetons par minute (fenêtre fixe, mesuré le 21/10/2026) et un appel
  en consomme environ 1 600.

### Key Entities *(include if feature involves data)*

- **Interprétation** : question saisie, question du catalogue retenue (ou ambiguïté, ou
  aucune), origine (mots-clés ou assistant).
- **Catalogue des questions** : les huit questions existantes de l'onglet Analyse, avec leur
  identifiant, leur libellé et leur portée ; il reste la seule liste que l'assistant peut
  choisir.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Sur un jeu de 40 formulations libres non reconnues par les mots-clés (5 par
  question), au moins 90 % aboutissent à la bonne question quand l'assistant est disponible.
- **SC-002**: 100 % des questions hors catalogue d'un jeu de 10 questions pièges (salaires,
  météo, instructions détournées) aboutissent à « question non reconnue », sans chiffre
  inventé.
- **SC-003**: Assistant indisponible, 100 % des reformulations reconnues aujourd'hui par les
  mots-clés le restent, et chaque réponse arrive en moins de 4 secondes.
- **SC-004**: Assistant disponible, 95 % des réponses à une question libre arrivent en moins
  de 3 secondes ; les questions reconnues par les mots-clés gardent leur délai actuel.
- **SC-005**: 100 % des réponses issues d'une question libre affichent comment la question a
  été comprise et par quel moyen.

## Assumptions

- Le catalogue des huit questions et leurs règles de calcul restent ceux de
  `specs/001-onglet-analyse` ; cette fonctionnalité ne change que la reconnaissance des
  questions libres.
- L'assistant est un modèle Claude Haiku d'Anthropic, utilisé comme outil de choix fermé
  (« tool » `rank(criterion, scope)`) ; la clé `LLM_KEY` du fichier `.env` (proxy d'une autre
  branche) n'est pas utilisée.
- Le délai de 3 secondes et le plafond de 30 questions par minute sont des valeurs par
  défaut, ajustables par configuration.
- Le serveur MCP « statsfoot », le skill d'ajout de critère et les hooks (étape 6 du cadrage)
  sont hors périmètre.
- L'onglet reste accessible sans compte ; aucune donnée de visiteur n'est conservée, ni par
  l'application ni dans des journaux (la question transmise n'est pas journalisée).
