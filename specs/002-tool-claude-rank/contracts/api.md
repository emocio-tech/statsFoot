# Contrat : Tool rank(scope, metric, order)

> **Amendement 2 du 2026-10-22 (constitution 2.2.0) : l'appel décrit ci-dessous n'existe plus.** Le tool `rank` n'est plus imposé au modèle
> (`tool_choice` vaut toujours `auto`), les tools de lecture sont exécutés par l'application et leurs résultats
> renvoyés au modèle (jusqu'à 6 appels), le délai est de 20 s (`LLM_TIMEOUT`), `max_tokens` vaut 400 au moins, et les
> propriétés `max-per-minute` et `tool-choice` sont supprimées. La réponse de `GET /api/analyse?q=` porte `status`
> (`FOUND`, `CHOOSE`, `FREE_TEXT`, `NOT_RECOGNIZED`), `text` et `interpretation.source` (`NAME` ou `ASSISTANT`).
>
> Amendement du 2026-10-22 (constitution 2.1.0) : la définition du tool (§2) est désormais `rank(scope, metric, order)`. Le modèle choisit une
> statistique de la liste fermée `StatMetrics` et un sens de tri, ou `NONE`. Une réponse générique a pour `question.id`
> `GENERIC:{PLAYER|TEAM}:{metric}:{DESC|ASC}`. Le `rank` du serveur MCP accepte `criterion` ou `scope` + `metric` +
> `order`.

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
| `system` | texte fixe : rôle (choisir une statistique à classer et un sens), les statistiques de joueur et d'équipe (nom et libellé), règles de choix et de tri, règle `NONE` hors classement |
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
    "description": "Choisit la statistique de joueur ou d'équipe à classer pour répondre à la question du visiteur, et le sens du tri. Ne calcule rien : le classement est calculé par l'application.",
    "parameters": {
      "type": "object",
      "additionalProperties": false,
      "required": ["scope", "metric", "order"],
      "properties": {
        "scope": { "type": "string", "enum": ["PLAYER", "TEAM", "UNKNOWN"] },
        "metric": { "type": "string", "enum": ["matches_played", "matches_started", "minutes_played", "goals", "...", "final_position", "NONE"] },
        "order": { "type": "string", "enum": ["DESC", "ASC"] }
      }
    }
  }
}
```

(`metric` liste toutes les statistiques de `StatMetrics` ; `...` abrège ici la liste.)

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
