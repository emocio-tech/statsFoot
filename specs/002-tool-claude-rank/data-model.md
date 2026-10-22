# Phase 1 – Modèle de données : Tool rank(scope, metric, order)

> **Amendement 2 du 2026-10-22 (constitution 2.2.0) : les sections 1 et 2 sont historiques.** Il n'y a plus d'entrée de tool à valider ni d'objet
> `Interpretation` : le modèle appelle directement des tools de lecture exécutés par l'application (voir
> `README.md`, « Tools de l'assistant »). Le flux devient : nom de joueur ou d'équipe → rapport ; sinon assistant LLM →
> texte (`FREE_TEXT`) ; sinon suggestions (`NOT_RECOGNIZED`). Le plafond d'usage de la section 5 est supprimé.
>
> Amendement du 2026-10-22 (constitution 2.1.0) : les sections 1 et 2 ci-dessous décrivent désormais `rank(scope, metric, order)` ; l'entrée à
> `criterion` et `alternatives` n'existe plus pour le modèle.

Aucune table ni migration : la fonctionnalité ne stocke rien (spec, hypothèses). Elle ajoute des objets
en mémoire et un champ à la réponse de l'onglet Analyse.

## 1. Entrée du tool `rank` (produite par Claude, validée par le serveur)

| Champ | Type | Règle |
|-------|------|-------|
| `scope` | `PLAYER` \| `TEAM` \| `UNKNOWN` | Requis. Doit correspondre à la portée de `metric` ; `UNKNOWN` avec `NONE`. |
| `metric` | énumération : toutes les statistiques de `StatMetrics` (joueur et équipe, ex. `goals`, `corners`, `goal_difference`), plus `NONE` | Requis. Les valeurs viennent de `StatMetrics` (jamais recopiées à la main). `NONE` : la question n'est pas un classement (mode libre). |
| `order` | `DESC` \| `ASC` | Requis. `DESC` du plus grand au plus petit, `ASC` du plus petit au plus grand. |

Validation côté serveur (FR-004) : schéma strict + contrôle de la statistique et de la cohérence de portée ;
tout écart → « aucune interprétation ».

## 2. Interprétation (objet interne)

| Champ | Valeurs |
|-------|---------|
| `outcome` | `RECOGNIZED`, `AMBIGUOUS`, `NONE` |
| `criterion` | question du catalogue retenue par les mots-clés (si `RECOGNIZED` par les mots-clés) |
| `alternatives` | questions proposées (si `AMBIGUOUS`, mots-clés seulement) |
| `generic` | classement choisi par l'assistant : `scope`, `metric`, `order` (si `RECOGNIZED` par l'assistant) |
| `unrelated` | vrai si l'assistant a répondu `NONE` : question hors classement, mode libre possible |
| `source` | `KEYWORDS` ou `ASSISTANT` |

Transitions dans `AnalyseService.answer(question)` :

```text
mots-clés ── reconnue ─────────────────────────────▶ RECOGNIZED (KEYWORDS)
          └─ non reconnue / ambiguë ─▶ assistant activé et sous le plafond ?
                                         ├─ non ──────────────▶ résultat des mots-clés
                                         └─ oui ─▶ appel (3 s)
                                                  ├─ statistique valide ─▶ RECOGNIZED (ASSISTANT, generic)
                                                  ├─ NONE ───────────────▶ mode libre (constitution VII)
                                                  └─ échec ──────────────▶ résultat des mots-clés
```

## 3. Réponse de l'API (ajout compatible)

`AnalyseAnswer` gagne `interpretation` (null pour une suggestion cliquée) :

| Champ | Description |
|-------|-------------|
| `source` | `KEYWORDS` ou `ASSISTANT` |
| `understoodAs` | libellé de la question retenue (null si non reconnue ou ambiguë) |

## 4. État de l'assistant

`AssistantStatus` : `enabled` (vrai si une clé est configurée). Exposé par `GET /api/analyse/assistant`.

## 5. Plafond d'usage

Compteur en mémoire, partagé : au plus `max-per-minute` (30) appels sur toute fenêtre glissante de 60 s.
