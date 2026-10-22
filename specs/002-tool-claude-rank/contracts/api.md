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

`enabled` est faux sans clé `LLM_KEY`. L'interface n'affiche la mention de transmission (FR-008)
que si `enabled` est vrai.

## 2. Appel au LLM du projet (format OpenAI Chat Completions, `POST /v1/chat/completions`, via le `ChatClient` de Spring AI)

| Élément | Valeur |
|---------|--------|
| Modèle | `claude-sonnet-4-6` (configurable ; `nova-lite` n'appelle pas le tool) |
| `max_tokens` | 200 pour le proxy, qui réserve ce plafond dans son quota (réponse d'environ 75 jetons) ; `max_completion_tokens` 1024 pour Bedrock (le raisonnement de gpt-oss compte dedans) |
| `system` | texte fixe : rôle (choisir une question du catalogue), les 8 questions (identifiant, libellé, portée, ce qu'elles mesurent), règle `NONE` hors catalogue, règle d'ambiguïté |
| `messages` | un message système (consignes) et un message utilisateur : la question saisie, telle quelle (jamais traitée comme un modèle de texte) |
| `tools` | un tool `function` `rank` (voir ci-dessous) ; jamais exécuté par Spring AI |
| `tool_choice` | `{ "type": "function", "function": { "name": "rank" } }` (`auto` si `LLM_TOOL_CHOICE=auto`) |
| Absent | `temperature`, `top_p`, `top_k`, `thinking`, données du tournoi, identité du visiteur |
| Client | délai 3 s (connexion et lecture), aucune nouvelle tentative (`RetryTemplate` à 1 essai) |

Définition du tool (format OpenAI ; le schéma est le même qu'avec le format Anthropic) :

```json
{
  "type": "function",
  "function": {
    "name": "rank",
    "description": "Choisit la question du catalogue de l'onglet Analyse qui correspond à la question du visiteur. Ne calcule rien : les réponses sont calculées par l'application.",
    "parameters": {
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
}
```

Lecture de la réponse : `choices[0].message.tool_calls` doit contenir un appel nommé `rank` dont `arguments` (chaîne JSON)
est validé par le serveur (data-model §1). Tout autre cas (texte seul, arguments illisibles, erreur HTTP, délai
dépassé) → aucune interprétation, repli sur les mots-clés. La consommation (`usage.prompt_tokens`,
`completion_tokens`) est journalisée ; la question ne l'est que par la trace des échanges (amendement du 2026-10-22 (constitution 2.0.0),
désactivable par ANALYSE_TRACE=false).

## 3. Configuration

Fournisseur choisi par `LLM_PROVIDER` : `proxy` (défaut) ou `bedrock`. Spring AI ajoute `/v1/chat/completions` à l'adresse.

| Propriété | Défaut | Rôle |
|-----------|--------|------|
| `analyse.assistant.provider` | `${LLM_PROVIDER:proxy}` | `proxy` ou `bedrock` |
| `analyse.assistant.api-key` | `${LLM_KEY:}` | clé du proxy ; vide → assistant désactivé |
| `analyse.assistant.base-url` | `${LLM_BASE_URL:https://g3jnmn4dyf.eu-west-3.awsapprunner.com}` | proxy LiteLLM de l'équipe |
| `analyse.assistant.model` | `${LLM_MODEL:claude-sonnet-4-6}` | modèle (`nova-lite` n'appelle pas le tool) |
| `analyse.assistant.timeout` | `${LLM_TIMEOUT:3s}` | délai d'un appel |
| `analyse.assistant.max-tokens` | `${LLM_MAX_TOKENS:200}` | plafond de la réponse (le proxy le réserve dans son quota) |
| `analyse.assistant.max-per-minute` | `${LLM_MAX_PER_MINUTE:1}` | plafond partagé (quota du proxy : 1 000 jetons par minute) |
| `analyse.assistant.tool-choice` | `${LLM_TOOL_CHOICE:forced}` | `forced` ou `auto` |
| `analyse.assistant.bedrock.api-key` | `${AWS_BEARER_TOKEN_BEDROCK:}` | jeton Bedrock ; vide → assistant désactivé |
| `analyse.assistant.bedrock.base-url` | `${BEDROCK_BASE_URL:https://bedrock-runtime.us-west-2.amazonaws.com/openai}` | Amazon Bedrock |
| `analyse.assistant.bedrock.model` | `${BEDROCK_MODEL:openai.gpt-oss-120b-1:0}` | modèle Bedrock |
| `analyse.assistant.bedrock.tool-choice` | `${BEDROCK_TOOL_CHOICE:forced}` | `forced` ou `auto` |
| `analyse.assistant.bedrock.max-tokens` | `${BEDROCK_MAX_TOKENS:1024}` | envoyé en `max_completion_tokens` |
| `analyse.assistant.bedrock.max-per-minute` | `${BEDROCK_MAX_PER_MINUTE:30}` | plafond partagé |
