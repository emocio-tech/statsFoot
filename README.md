# statsFoot

Sports analysis of the 2026 World Cup. Current feature: **player profile search** — type a player name, get the player's team and tournament statistics (minutes, goals, cards, duels, distance, ratings...).

## Requirements

- Java 21 (Maven is provided by the wrapper `./mvnw`)
- Node.js and npm (for the Angular FrontEnd in `frontend/`)

## Data

The tournament data comes from `data/donneesStats.json` (48 teams, 1039 players, 104 matches). The schema of the database is described by `data/donneesStats.sql` (PostgreSQL reference script, not executed by the application) and ported to H2 in `src/main/resources/db/schema.sql`.

At startup the application creates an **H2 file database** (`data/h2/statsfoot.mv.db`, not versioned) and fills it from `donneesStats.json` when it is empty. Later starts reuse the database. To reload the data, stop the application and delete the folder `data/h2/`.

If the database or the import fails, the application still starts and the API answers `503`.

## Run

```
./mvnw spring-boot:run     # BackEnd on http://localhost:8080 (first start creates and fills data/h2/)
cd frontend
npm install                # first time only
npm start                  # FrontEnd on http://localhost:4200 (proxies /api to :8080)
```

## Tests

```
./mvnw test                # BackEnd (in-memory H2, never writes data/h2/)
cd frontend && npm test    # FrontEnd (non interactive)
```

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `statsfoot.database-file` | `./data/h2/statsfoot` | H2 database file (without extension). H2 needs an explicit `./` or an absolute path. |
| `statsfoot.data-file` | `data/donneesStats.json` | Data file imported when the database is empty |
| `spring.datasource.url` | H2 file URL built from `statsfoot.database-file` | Datasource (tests use an in-memory H2) |

## API

- `GET /api/players?name=<text>` — search by name (case, accents and extra spaces ignored, partial match). Returns `{ total, truncated, players[] }` with `id`, `name`, `teamCode`, `teamName`; at most 50 players sorted by name. `400` if the name is blank or missing.
- `GET /api/players/{id}` — profile: name, team, goalkeeper and captain match counts and the tournament totals (`stats`: matches, minutes, goals, cards, fouls, free kicks, duels, corners, offsides, distance, man of the match, ratings). A statistic without value is `null`. `404` if the id is unknown or not a number.
- `503` with `{ "error": "Player data is currently unavailable." }` when the database cannot be read or the import failed.

The specifications, plans and tasks are in `specs/` (`player-profile-search`, amended by `player-h2-database`).
