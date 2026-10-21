# Contrat : Tool Claude rank(criterion, scope)

## 1. Routes de l'application

### GET /api/analyse?q={question} (existante, réponse enrichie)

Même comportement que dans `specs/001-onglet-analyse/contracts/api.md`, avec deux changements :

- une question non reconnue ou ambiguë par les mots-clés peut être interprétée par l'assistant ;
- la réponse gagne `interpretation`.

```json
{
  "status": "ANSWERED",
  "question": { "id": "TOP_SCORING_TEAM", "label": "Équipe qui a marqué le plus de buts", "scope": "TEAM", "fictitious": false },
  "interpretation": { "source": "ASSISTANT", "understoodAs": "Équipe qui a marqué le plus de buts" },
  "...": "winners, top, explanation, fictitious, candidates comme avant"
}
```

`interpretation.source` vaut `KEYWORDS` quand les mots-clés ont suffi. Pour `GET /api/analyse/{questionId}`
(suggestion cliquée), `interpretation` vaut `null` et l'assistant n'est jamais consulté.

### GET /api/analyse/assistant (nouvelle)

```json
{ "enabled": true }
```

`enabled` est faux sans clé `ANTHROPIC_API_KEY`. L'interface n'affiche la mention de transmission (FR-008)
que si `enabled` est vrai.

## 2. Appel à l'API Claude (Messages API, `POST /v1/messages` via le SDK Java)

| Élément | Valeur |
|---------|--------|
| Modèle | `claude-haiku-5-5` |
| `max_tokens` | 1024 |
| `system` | texte fixe : rôle (choisir une question du catalogue), les 8 questions (identifiant, libellé, portée, ce qu'elles mesurent), règle `NONE` hors catalogue, règle d'ambiguïté |
| `messages` | un seul message utilisateur : la question saisie, telle quelle |
| `tools` | `rank` (voir ci-dessous), `strict: true` |
| `tool_choice` | `{ "type": "tool", "name": "rank" }` |
| Absent | `temperature`, `top_p`, `top_k`, `thinking`, données du tournoi, identité du visiteur |
| Client | délai 3 s, aucune nouvelle tentative |

Définition du tool :

```json
{
  "name": "rank",
  "description": "Choisit la question du catalogue de l'onglet Analyse qui correspond à la question du visiteur. Ne calcule rien.",
  "strict": true,
  "input_schema": {
    "type": "object",
    "additionalProperties": false,
    "required": ["criterion", "scope", "alternatives"],
    "properties": {
      "criterion": { "type": "string", "enum": ["TOP_SCORER", "TOP_ASSIST", "MOST_YELLOW_CARDS", "LEAST_FAIR_PLAY_PLAYER", "BEST_TEAM", "TOP_SCORING_TEAM", "LEAST_FAIR_PLAY_TEAM", "MOST_FAIR_PLAY_PLAYER", "NONE"] },
      "scope": { "type": "string", "enum": ["PLAYER", "TEAM", "UNKNOWN"] },
      "alternatives": { "type": "array", "items": { "type": "string", "enum": ["TOP_SCORER", "TOP_ASSIST", "MOST_YELLOW_CARDS", "LEAST_FAIR_PLAY_PLAYER", "BEST_TEAM", "TOP_SCORING_TEAM", "LEAST_FAIR_PLAY_TEAM", "MOST_FAIR_PLAY_PLAYER"] } }
    }
  }
}
```

Lecture de la réponse : `stop_reason` doit valoir `tool_use` et le contenu porter un bloc `tool_use` nommé
`rank` ; son entrée est validée (data-model §1). Tout autre cas (dont `refusal`, `max_tokens`, erreur,
délai dépassé) → aucune interprétation, repli sur les mots-clés.

## 3. Configuration

| Propriété | Défaut | Rôle |
|-----------|--------|------|
| `analyse.assistant.api-key` | `${ANTHROPIC_API_KEY:}` | vide → assistant désactivé |
| `analyse.assistant.model` | `claude-haiku-5-5` | modèle |
| `analyse.assistant.timeout` | `3s` | délai d'un appel |
| `analyse.assistant.max-per-minute` | `30` | plafond partagé |
