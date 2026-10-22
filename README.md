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

### Assistant d'interprétation (tool `rank`, Spring AI)

Quand les mots-clés ne reconnaissent pas une question libre de l'onglet Analyse (ou la jugent ambiguë), le backend
peut la faire interpréter par un LLM, via le `ChatClient` de Spring AI (format OpenAI Chat Completions) et un
unique tool `rank(criterion, scope)`. Le modèle ne fait que choisir une des 8 questions du catalogue (ou aucune) ;
le tool n'est jamais exécuté et les chiffres viennent toujours du SQL. Seule la question saisie est transmise ;
elle n'est journalisée que par la trace des échanges (voir ci-dessous ; `ANALYSE_TRACE=false` la désactive), sinon seuls le nombre de jetons et le type d'une erreur le sont. Délai de 3 s, aucune
nouvelle tentative, repli silencieux sur les mots-clés.

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
| `LLM_MODEL` | `claude-sonnet-4-6` | modèle (doit savoir appeler un tool imposé ; `nova-lite` ne le sait pas) |
| `LLM_MAX_TOKENS` | `200` | plafond de la réponse ; le proxy le réserve dans son quota |
| `LLM_MAX_PER_MINUTE` | `1` | plafond d'appels par minute ; le proxy limite la clé à 1 000 jetons par minute et un appel en consomme environ 1 600 : à relever avec la limite de la clé |
| `LLM_TOOL_CHOICE` | `forced` | `forced` (tool imposé) ou `auto` |
| `AWS_BEARER_TOKEN_BEDROCK` | (vide) | jeton Bedrock (fournisseur `bedrock`) ; vide → assistant désactivé |
| `BEDROCK_BASE_URL` | `https://bedrock-runtime.us-west-2.amazonaws.com/openai` | adresse Bedrock |
| `BEDROCK_MODEL` | `openai.gpt-oss-120b-1:0` | modèle Bedrock |
| `BEDROCK_MAX_TOKENS` | `1024` | envoyé en `max_completion_tokens` (le raisonnement de gpt-oss y compte) |
| `BEDROCK_TOOL_CHOICE` | `forced` | `forced` ou `auto`, si le modèle refuse de se voir imposer un tool |
| `BEDROCK_MAX_PER_MINUTE` | `30` | plafond d'appels par minute (Bedrock) |

Une variable d'environnement reste prioritaire sur le fichier `.env`.

| Route | Contenu |
|---|---|
| `GET /api/analyse/assistant` | `{"enabled": true\|false}` : assistant configuré ou non |

La réponse de `GET /api/analyse?q=...` indique `interpretation.source` (`KEYWORDS` ou `ASSISTANT`) et
`interpretation.understoodAs`. Évaluation réelle (50 requêtes) :
`LLM_KEY=... ./mvnw -pl backend test -Dtest=AssistantEvalTest -Danalyse.assistant.eval=true
-Danalyse.assistant.eval.pause-seconds=62` (le quota du proxy impose cette pause : près d'une heure).

### Serveur MCP « statsfoot » (lecture seule)

Le backend expose les mêmes fonctions que l'API comme serveur MCP (Spring AI, transport SSE sur `/sse`), pour
interroger les statistiques en langage naturel depuis Claude Code ou Claude Desktop. Aucune écriture : mêmes
requêtes et mêmes vues que l'API ; les données fictives restent signalées.

| Outil | Rôle |
|---|---|
| `questions` | les questions du catalogue (identifiant, libellé, portée, fictif) |
| `rank(criterion)` | classement d'une question : vainqueurs, top 5, règle, fictif |
| `find(name)` | recherche d'un joueur (nom et/ou prénom) ou d'une équipe : identifiants |
| `player_report(playerId)` | rapport complet d'un joueur |
| `team_report(teamId)` | rapport complet d'une équipe |

Application démarrée (`http://localhost:8080`), Claude Code lit `.mcp.json` à la racine ; sinon :
`claude mcp add --transport sse statsfoot http://localhost:8080/sse`.

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
retour (`<--`), entre l'appli, le LLM, le tool `rank`, le SQL et les clients MCP, puis les renvoie dans le champ `trace`
de la réponse : l'interface les affiche sous le résultat (« Échanges de la recherche »). Les appels reçus par le serveur
MCP (`questions`, `rank`, `find`, `player_report`, `team_report`) sont journalisés de la même façon. La trace contient
la question saisie ; `ANALYSE_TRACE=false` la supprime des journaux et de la réponse.

### Mode libre : tools `stat` et `calculate`

Quand le tool `rank` juge une question sans rapport avec le catalogue (y compris un total, une moyenne, un écart ou une
valeur pour un joueur ou une équipe nommé), le LLM la traite lui-même en enchaînant des tools de lecture, en plusieurs
allers-retours, puis rédige une réponse courte affichée en gras (statut `FREE_TEXT`) :

| Tool | Rôle |
|---|---|
| `stat(scope, metric, aggregate, name, team, order, limit)` | lit une statistique de joueur ou d'équipe des vues du tournoi : liste classée, `SUM`, `AVG`, `MAX`, `MIN`, `COUNT`, filtrée par nom ou par équipe ; statistique choisie dans une liste fermée (pas de SQL libre) ; signale les données fictives |
| `calculate(expression)` | arithmétique exacte (`+ - * / %`, parenthèses), sans variable ni fonction |
| `find`, `player_report`, `team_report`, `rank`, `questions` | les tools du serveur MCP, réutilisés tels quels |

Ex. : « de combien de pour cent Mbappé a-t-il marqué plus de buts que Yamal ? » donne `stat` (Mbappé), `stat` (Yamal),
puis `calculate`. Ce mode demande plusieurs appels LLM par question : relever `LLM_MAX_PER_MINUTE` (6 dans `.env`).
`stat` et `calculate` sont aussi exposés par le serveur MCP (lecture seule).
