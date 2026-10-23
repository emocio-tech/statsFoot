# statsFoot
Analyse sportive

Projet Maven multi-module **Analyse Foot** : backend Spring Boot 3.5 (Java 25) et frontend Angular 20.

## Prérequis

- JDK 25 (`JAVA_HOME` doit pointer dessus)
- Maven : inutile de l'installer, le wrapper `mvnw` télécharge Maven 3.9.16
- Node n'est pas nécessaire pour le build Maven (il est téléchargé dans `frontend/node/`). Il l'est en revanche pour le dev front avec `ng serve`.

## Arborescence

```
.
├── pom.xml                      # POM parent (modules frontend + backend)
├── backend/                     # Spring Boot
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/emocio/analysefoot/
│       │   ├── AnalyseFootApplication.java
│       │   ├── config/          # configuration, routage SPA
│       │   ├── controller/      # API REST (/api/**)
│       │   ├── model/           # DTO / entités
│       │   ├── repository/      # accès aux données
│       │   └── service/         # logique métier
│       ├── main/resources/application.yml
│       └── test/java/...
└── frontend/                    # Angular
    ├── pom.xml                  # build Angular via frontend-maven-plugin
    ├── package.json / angular.json / proxy.conf.json
    └── src/app/
        ├── core/                # services et modèles partagés
        ├── features/            # pages (home, ...)
        └── shared/              # composants réutilisables
```

## Base de données

PostgreSQL. Le schéma est géré par Flyway (`backend/src/main/resources/db/migration`) et appliqué au démarrage.

```bash
docker compose up -d   # base analysefoot / utilisateur analysefoot / mdp analysefoot
```

La connexion se surcharge avec les variables `DB_URL`, `DB_USERNAME` et `DB_PASSWORD`.

### Import des données

Le fichier `worldcup-full.json` (format openfootball) est importé au démarrage si la propriété `analyse-foot.import.worldcup-file` est renseignée :

```bash
java -jar backend/target/analyse-foot.jar --analyse-foot.import.worldcup-file=worldcup-full.json
```

L'import est ignoré si une compétition du même nom existe déjà en base.

### Notes des matchs

Les notes (0 à 10) des joueurs et des équipes sont calculées par le skill Claude Code `notation-matchs` (`.claude/skills/notation-matchs`). L'application les affiche dans les pages Matchs et Classements, et les expose via l'API :

| Route | Contenu |
|---|---|
| `GET /api/matches` | matchs avec score et note de chaque équipe |
| `GET /api/matches/{id}/ratings` | notes des équipes et des joueurs d'un match |
| `GET /api/rankings/players?minMatches=3&limit=20` | joueurs par note moyenne |
| `GET /api/rankings/teams` | équipes par note moyenne |

### Statistiques du tournoi et onglet Analyse

`backend/src/main/resources/db/migration/donneesStats.sql` regroupe le schéma (copie des migrations V1 à V4),
le calcul des notes et la génération de statistiques détaillées **fictives** : fautes, coups francs, duels,
corners, hors-jeu, distance, homme du match et passes décisives. La migration V4 ajoute le gardien de chaque
feuille de match (donnée réelle) et la table des passes décisives.

Sur une base gérée par Flyway, n'exécuter que les parties de données (3 : notes, 6 : stats fictives),
jamais le schéma :

```bash
F=backend/src/main/resources/db/migration/donneesStats.sql
{ echo '\set ON_ERROR_STOP on'
  sed -n '/^-- PARTIE 3 /,/^-- PARTIE 4 /p' "$F" | sed '$d'
  sed -n '/^-- PARTIE 6 /,$p' "$F"; } | psql -U analysefoot -h localhost -d analysefoot
```

| Route | Contenu |
|---|---|
| `GET /api/stats/players` | totaux du tournoi par joueur (onglet Joueurs) ; `fictitiousFields` liste les champs fictifs |
| `GET /api/stats/teams` | totaux du tournoi par équipe (onglet Équipes) ; `fictitiousFields` liste les champs fictifs |
| `GET /api/analyse/questions` | les 8 questions de l'onglet Analyse |
| `GET /api/analyse/{questionId}` | réponse à une question : gagnants (ex aequo compris), critère, top 5 |
| `GET /api/analyse?q=...` | question libre (reconnue, ambiguë ou non reconnue) ou nom d'un joueur (nom et/ou prénom) ou d'une équipe : statut `FOUND` (une correspondance, dans `top`) ou `CHOOSE` (plusieurs, à départager) ; l'interface affiche alors le rapport complet |
| `GET /api/players/{id}/report` | rapport complet d'un joueur, dont son rang sur chaque critère |
| `GET /api/teams/{id}/report` | rapport complet d'une équipe : parcours, position finale, effectif |

Les stats détaillées et les passes décisives sont inventées (cohérentes avec les matchs réels) : l'interface
les signale par un astérisque et la mention « données fictives ».

### Assistant LLM de l'onglet Analyse (Spring AI)

La recherche de l'onglet Analyse se passe ainsi :
1. le nom d'un joueur ou d'une équipe (nom et/ou prénom) affiche son rapport complet ;
2. **toute autre saisie est confiée en priorité au LLM**, via le `ChatClient` de Spring AI (format OpenAI Chat
   Completions). Il l'interprète librement et y répond en enchaînant les tools de lecture, que l'application exécute
   (lecture seule, SQL) : `metrics`, `rank`, `stat`, `find`, `player_report`, `team_report`, `matches`, `calculate`
   (voir plus bas). Sa réponse courte s'affiche en gras (statut `FREE_TEXT`) ;
3. sans clé LLM, ou si le modèle n'aboutit pas, les 8 questions suggérées sont proposées (clic). Elles ne servent plus à
   interpréter une saisie : il n'y a plus de reconnaissance par mots-clés.

Aucun plafond d'appels par minute ; seul garde-fou : 6 allers et retours LLM, tool au plus par saisie. Seule la question
saisie est transmise au modèle (aucune donnée de visiteur) ; elle n'est journalisée que par la trace des échanges (voir
plus bas ; `ANALYSE_TRACE=false` la désactive), sinon seuls le nombre de jetons et le type d'une erreur le sont. Délai de
20 s par appel, aucune nouvelle tentative.

Deux fournisseurs, choisis par `LLM_PROVIDER` :
- `proxy` (défaut) : le LLM du projet, `claude-sonnet-4-6` sur le proxy LiteLLM de l'équipe ;
- `bedrock` : Amazon Bedrock, modèle `openai.gpt-oss-120b-1:0` (non testé en réel : jeton absent).

L'assistant est **désactivé** tant que la clé du fournisseur n'est pas fournie (côté serveur, jamais commitée). Le
plus simple : le fichier `.env` à la racine du projet (ignoré par git), lu au démarrage quand l'application est
lancée depuis la racine :

```bash
echo LLM_KEY=... > .env
java -jar backend/target/analyse-foot.jar
```

| Variable | Défaut | Rôle |
|---|---|---|
| `LLM_PROVIDER` | `proxy` | `proxy` ou `bedrock` |
| `LLM_KEY` | (vide) | clé du proxy ; vide → assistant désactivé |
| `LLM_BASE_URL` | `https://g3jnmn4dyf.eu-west-3.awsapprunner.com` | adresse du proxy (Spring AI ajoute `/v1/chat/completions`) |
| `LLM_MODEL` | `claude-sonnet-4-6` | modèle (doit savoir appeler des tools ; `nova-lite` ne le sait pas) |
| `LLM_TIMEOUT` | `20s` | délai d'un appel au modèle (plusieurs par saisie) |
| `LLM_MAX_TOKENS` | `400` | plafond de la réponse ; le proxy le réserve dans son quota (400 au minimum) |
| `AWS_BEARER_TOKEN_BEDROCK` | (vide) | jeton Bedrock (fournisseur `bedrock`) ; vide → assistant désactivé |
| `BEDROCK_BASE_URL` | `https://bedrock-runtime.us-west-2.amazonaws.com/openai` | adresse Bedrock |
| `BEDROCK_MODEL` | `openai.gpt-oss-120b-1:0` | modèle Bedrock |
| `BEDROCK_MAX_TOKENS` | `1024` | envoyé en `max_completion_tokens` (le raisonnement de gpt-oss y compte) |

Une variable d'environnement reste prioritaire sur le fichier `.env`.

| Route | Contenu |
|---|---|
| `GET /api/analyse/assistant` | `{"enabled": true\|false}` : assistant configuré ou non |

La réponse de `GET /api/analyse?q=...` porte `status` (`FOUND`, `CHOOSE`, `FREE_TEXT`, `NOT_RECOGNIZED`), `text` (la
réponse du LLM pour `FREE_TEXT`) et `interpretation.source` (`NAME` ou `ASSISTANT`).

### Serveur MCP « statsfoot » (lecture seule)

Le backend expose les mêmes fonctions que l'API comme serveur MCP (Spring AI, transport STREAMABLE sur `/mcp`), pour
interroger les statistiques en langage naturel depuis Claude Code ou Claude Desktop. Aucune écriture : mêmes
requêtes et mêmes vues que l'API ; les données fictives restent signalées.

| Outil | Rôle |
|---|---|
| `metrics` | les statistiques de joueur et d'équipe disponibles (nom, libellé, fictif) |
| `rank(scope, metric, order)` | classement d'une statistique : vainqueurs, top 5 avec ex aequo, règle, fictif |
| `find(name)` | recherche d'un joueur (nom et/ou prénom) ou d'une équipe : identifiants |
| `player_report(playerId)` | rapport complet d'un joueur |
| `team_report(teamId)` | rapport complet d'une équipe |

Application démarrée (`http://localhost:8080`), Claude Code lit `.mcp.json` à la racine ; sinon :
`claude mcp add --transport http statsfoot http://localhost:8080/mcp`.

## Recherche et comparaison (issues de `main`)

Fonctions reprises de la branche `main`, désormais branchées sur la base PostgreSQL :

- recherche d'un joueur par son nom (profil et statistiques du tournoi) ;
- comparaison de deux joueurs et de deux équipes (leader sur chaque statistique, verdict rédigé) ;
- chat LLM (`/api/chat`) et outils MCP `searchPlayers` / `comparePlayers`, exposés avec le protocole **STREAMABLE**.

L'ancienne base H2 et l'import de `data/donneesStats.json` ne sont plus utilisés : PostgreSQL est la seule source de données.

## Build et lancement

```bash
./mvnw clean package
java -jar backend/target/analyse-foot.jar
```

L'application (front + API) est servie sur http://localhost:8080, et l'API de test sur `/api/status`.

## Tests

```bash
./mvnw -pl backend test
```

Les tests d'import et de notation utilisent une base PostgreSQL dédiée, vidée à chaque test : `analysefoot_test` (même utilisateur que la base principale, surchargeable par `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`). Ils sont ignorés si elle est injoignable.

```sql
CREATE DATABASE analysefoot_test OWNER analysefoot;
```

Un hook Git lance le build Angular et ces tests avant chaque commit et l'annule en cas d'échec. À activer une
fois par clone :

```bash
git config core.hooksPath .githooks
```

`git commit --no-verify` permet de passer outre ponctuellement.

## Développement

```bash
# Terminal 1 : backend
./mvnw -pl backend spring-boot:run

# Terminal 2 : frontend avec rechargement à chaud (proxy /api -> 8080)
cd frontend
npm install
npm start
```

Le front est alors servi sur http://localhost:4200.

### Trace des échanges (logs et interface)

Chaque recherche de l'onglet Analyse journalise (logger `analyse.trace`, niveau INFO) ses échanges, aller (`-->`) et
retour (`<--`), entre l'appli, le LLM, ses tools de lecture, le SQL et les clients MCP, puis les renvoie dans le champ `trace`
de la réponse : l'interface les affiche sous le résultat (« Échanges de la recherche »). Les appels reçus par le serveur
MCP (`metrics`, `rank`, `find`, `player_report`, `team_report`) sont journalisés de la même façon. La trace contient
la question saisie ; `ANALYSE_TRACE=false` la supprime des journaux et de la réponse.

### Tools de l'assistant : `stat`, `matches`, `calculate` et `rank`

Le LLM choisit lui-même, saisie par saisie, quels tools enchaîner, en plusieurs allers-retours :

| Tool | Rôle |
|---|---|
| `metrics` | liste les statistiques de joueur et d'équipe disponibles (liste fermée `StatMetrics`) |
| `rank(scope, metric, order)` | classe les joueurs ou les équipes sur une statistique : `RANK()` en SQL, ex aequo conservés, top 5 ; plus aucun critère prédéfini |
| `stat(scope, metric, aggregate, name, team, order, limit)` | lit une statistique : liste classée, `SUM`, `AVG`, `MAX`, `MIN`, `COUNT`, filtrée par nom ou par équipe ; statistique choisie dans une liste fermée (pas de SQL libre) ; signale les données fictives |
| `matches(team, stage, limit)` | les matchs : date, phase, groupe, stade, équipes, score (prolongation, tirs au but), notes |
| `find`, `player_report`, `team_report` | identité et rapport complet d'un joueur ou d'une équipe |
| `calculate(expression)` | arithmétique exacte (`+ - * / %`, parenthèses), sans variable ni fonction |

Ex. : « de combien de pour cent Mbappé a-t-il marqué plus de buts que Yamal ? » donne `stat` (Mbappé), `stat` (Yamal),
puis `calculate`. Tous sont aussi exposés par le serveur MCP, en lecture seule. Les 8 questions de l'enum
`AnalyseCriterion` ne servent plus qu'aux suggestions cliquables de l'interface.
