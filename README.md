# statsFoot

Sports analysis of the 2026 World Cup. Current feature: **player profile search** — type a player name, get the player's identity and tournament statistics.

## Requirements

- Java 21 (Maven is provided by the wrapper `./mvnw`)
- Node.js and npm (for the Angular FrontEnd in `frontend/`)

## Data files

The JSON data files are **not** versioned on `main` nor on feature branches; they live on the `data` branch. To run the application or the BackEnd tests locally, extract them without staging them:

```
git fetch origin data
git archive origin/data data/players.json data/teams.json | tar -x
echo "/data/*.json" >> .git/info/exclude
```

Without these files the data-dependent tests fail with `data files missing: extract them from the data branch`, and the API answers `503`.

## Run

```
./mvnw spring-boot:run     # BackEnd on http://localhost:8080
cd frontend
npm install                # first time only
npm start                  # FrontEnd on http://localhost:4200 (proxies /api to :8080)
```

## Tests

```
./mvnw test                # BackEnd
cd frontend && npm test    # FrontEnd (non interactive)
```

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `statsfoot.data-dir` | `data` | Folder containing `players.json` and `teams.json` |
| `statsfoot.player-source` | `json` | `json` reads the files above. `database` selects the database adapter, which is built but unused. Activate it with the Spring profile `database` (it sets `player-source=database`) and provide `spring.datasource.*`. |

## API

- `GET /api/players?name=<text>` — search by name (case, accents and extra spaces ignored, partial match). Returns `{ total, truncated, players[] }`, at most 50 players sorted by name. `400` if the name is blank or missing.
- `GET /api/players/{id}` — full profile (identity, birth date, age computed at the current date, tournament statistics, goalkeeper statistics for goalkeepers). `404` if unknown.
- `503` with `{ "error": "Player data is currently unavailable." }` when the data cannot be read.

The specification, plan and tasks of the feature are in `specs/player-profile-search/`.
