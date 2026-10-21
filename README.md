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
| `GET /api/analyse?q=...` | question libre, reconnue par mots-clés (reconnue, ambiguë ou non reconnue) |
| `GET /api/players/{id}/report` | rapport complet d'un joueur, dont son rang sur chaque critère |
| `GET /api/teams/{id}/report` | rapport complet d'une équipe : parcours, position finale, effectif |

Les stats détaillées et les passes décisives sont inventées (cohérentes avec les matchs réels) : l'interface
les signale par un astérisque et la mention « données fictives ».

### Assistant d'interprétation (tool Claude `rank`)

Quand les mots-clés ne reconnaissent pas une question libre de l'onglet Analyse (ou la jugent ambiguë),
le backend peut la faire interpréter par Claude Haiku 5.5 (`claude-haiku-5-5`) avec un unique tool
`rank(criterion, scope)` : le modèle ne fait que choisir une des 8 questions du catalogue (ou aucune) ;
les chiffres viennent toujours du SQL. Seule la question saisie est transmise à Anthropic ; elle n'est
pas journalisée. Délai de 3 s, plafond de 30 appels par minute, repli silencieux sur les mots-clés.

L'assistant est **désactivé** tant que la variable d'environnement `ANTHROPIC_API_KEY` n'est pas définie
(côté serveur, jamais commitée) :

```bash
ANTHROPIC_API_KEY=sk-ant-... java -jar backend/target/analyse-foot.jar
```

| Route | Contenu |
|---|---|
| `GET /api/analyse/assistant` | `{"enabled": true\|false}` : assistant configuré ou non |

La réponse de `GET /api/analyse?q=...` indique `interpretation.source` (`KEYWORDS` ou `ASSISTANT`) et
`interpretation.understoodAs`. Évaluation réelle (payante, moins d'un centime) :
`ANTHROPIC_API_KEY=... ./mvnw -pl backend test -Dtest=AssistantEvalTest -Danalyse.assistant.eval=true`.

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
