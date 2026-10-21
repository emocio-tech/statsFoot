# Contrat d'API : Onglet Analyse

Toutes les routes sont en lecture (`GET`), sous `/api`, en JSON. Elles portent sur la dernière
compétition importée. Une valeur inconnue vaut `null`, jamais `0` (constitution VI).
Les objets sont décrits dans [data-model.md](../data-model.md).

## GET /api/analyse/questions

Liste les questions du catalogue, dans l'ordre des suggestions.

```json
[
  { "id": "TOP_SCORER", "label": "Meilleur buteur", "scope": "PLAYER", "fictitious": false },
  { "id": "TOP_ASSIST", "label": "Meilleur passeur", "scope": "PLAYER", "fictitious": true }
]
```

## GET /api/analyse?q={question}

Reconnaît une question libre puis y répond.

| Cas | HTTP | `status` |
|-----|------|----------|
| question reconnue, données présentes | 200 | `ANSWERED` |
| plusieurs questions possibles | 200 | `AMBIGUOUS` (`candidates` renseigné, pas de classement) |
| aucune question reconnue | 200 | `NOT_RECOGNIZED` (`candidates` = toutes les questions) |
| question reconnue, aucune donnée | 200 | `NO_DATA` |
| `q` absent ou vide | 400 | — (`{"error": "Veuillez saisir une question."}`) |

Exemple (`q=qui a marqué le plus ?`) :

```json
{
  "status": "ANSWERED",
  "question": { "id": "TOP_SCORER", "label": "Meilleur buteur", "scope": "PLAYER", "fictitious": false },
  "explanation": "Buts hors but contre son camp ; égalité départagée par le moins de minutes jouées.",
  "fictitious": false,
  "winners": [
    { "rank": 1, "scope": "PLAYER", "id": 664, "name": "Kylian MBAPPE", "team": "France",
      "value": 10, "unit": "buts", "tieBreakValue": 698, "valueLabel": null }
  ],
  "top": [
    { "rank": 1, "scope": "PLAYER", "id": 664, "name": "Kylian MBAPPE", "team": "France", "value": 10, "unit": "buts", "tieBreakValue": 698 },
    { "rank": 2, "scope": "PLAYER", "id": 756, "name": "Lionel MESSI", "team": "Argentina", "value": 8, "unit": "buts", "tieBreakValue": 740 }
  ],
  "candidates": []
}
```

## GET /api/analyse/{questionId}

Répond directement à une question du catalogue (suggestion cliquée). Même corps que ci-dessus,
`status` vaut `ANSWERED` ou `NO_DATA`. `questionId` inconnu : 404 `{"error": "Question inconnue."}`.
Le chemin littéral `/api/analyse/questions` est prioritaire ; aucun identifiant de question ne
vaut `questions`.

## GET /api/players/{id}/report

Rapport complet d'un joueur (`id` = identifiant numérique de la base). 404 sans corps si inconnu.

```json
{
  "playerId": 664, "name": "Kylian MBAPPE", "teamId": 33, "team": "France", "goalkeeper": false,
  "totals": { "matchesPlayed": 8, "minutesPlayed": 698, "goals": 10, "assists": 2, "yellowCards": 1,
              "fairPlayPoints": -1, "decisiveGoals": 3, "foulsCommitted": 9, "distanceKm": 80.4,
              "manOfTheMatchCount": 5, "rating": 8.1, "ratingWeighted": 8.1 },
  "matches": [
    { "matchId": 12, "kickoffAt": "2026-06-16T19:00:00Z", "stage": "GROUP", "opponent": "Senegal",
      "score": "3-1", "minutesPlayed": 90, "goals": 2, "assists": 0, "yellowCards": 0, "sentOff": false,
      "rating": 9.1 }
  ],
  "bestMatch": { "matchId": 12, "rating": 9.1 },
  "ranks": [ { "questionId": "TOP_SCORER", "label": "Meilleur buteur", "rank": 1, "outOf": 140 },
             { "questionId": "MOST_FAIR_PLAY_PLAYER", "label": "Joueur le plus fair-play", "rank": null, "outOf": 300 } ],
  "fictitiousFields": ["assists", "foulsCommitted", "distanceKm", "manOfTheMatchCount"]
}
```

Les valeurs de l'exemple sont illustratives, hormis les buts, matchs et minutes (réels).

## GET /api/teams/{id}/report

Rapport complet d'une équipe. 404 sans corps si inconnue.

```json
{
  "teamId": 29, "team": "Spain",
  "totals": { "matchesPlayed": 8, "wins": 7, "draws": 1, "losses": 0, "points": 22, "goalsFor": 14,
              "goalsAgainst": 1, "fairPlayPoints": -6, "foulsCommitted": 88, "rating": 8.9,
              "stageReached": "FINAL", "finalPosition": 1 },
  "matches": [
    { "matchId": 5, "kickoffAt": "2026-06-14T16:00:00Z", "stage": "GROUP", "opponent": "...",
      "score": "2-0", "penalties": null, "rating": 8.4 }
  ],
  "squad": [ { "playerId": 1, "name": "...", "matchesPlayed": 8, "goals": 3, "ratingWeighted": 7.5 } ],
  "topScorer": { "playerId": 1, "name": "...", "goals": 5 },
  "bestPlayer": { "playerId": 1, "name": "...", "ratingWeighted": 7.6 },
  "fictitiousFields": ["foulsCommitted", "freeKicksWon", "duelsWon", "corners", "offsides", "distanceKm"]
}
```

## Routes existantes : ajout compatible

`GET /api/stats/players` et `GET /api/stats/teams` gardent leurs champs actuels et gagnent, sur
chaque ligne, `fictitiousFields` (liste des champs d'origine fictive, voir data-model §1). Les
colonnes ajoutées aux vues par la V4 n'y sont pas exposées (elles le sont dans les rapports).

```json
{ "playerId": 664, "player": "Kylian MBAPPE", "foulsCommitted": 9, "...": "...",
  "fictitiousFields": ["foulsCommitted", "freeKicksWon", "freeKicksConceded", "duelsWon", "duelsLost",
                       "corners", "offsides", "distanceKm", "manOfTheMatchCount", "manOfTheMatch"] }
```

## Interface (routes Angular)

| Route | Contenu |
|-------|---------|
| `/analyse` | barre de recherche, 8 suggestions, réponse, rapport, top 5 |
| `/joueurs/:id` | rapport joueur (ouvert depuis l'onglet Joueurs ou une réponse) |
| `/equipes/:id` | rapport équipe (ouvert depuis l'onglet Équipes ou une réponse) |
