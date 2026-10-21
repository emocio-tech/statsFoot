# Phase 1 – Modèle de données : Tool Claude rank(criterion, scope)

Aucune table ni migration : la fonctionnalité ne stocke rien (spec, hypothèses). Elle ajoute des objets
en mémoire et un champ à la réponse de l'onglet Analyse.

## 1. Entrée du tool `rank` (produite par Claude, validée par le serveur)

| Champ | Type | Règle |
|-------|------|-------|
| `criterion` | énumération : `TOP_SCORER`, `TOP_ASSIST`, `MOST_YELLOW_CARDS`, `LEAST_FAIR_PLAY_PLAYER`, `BEST_TEAM`, `TOP_SCORING_TEAM`, `LEAST_FAIR_PLAY_TEAM`, `MOST_FAIR_PLAY_PLAYER`, `NONE` | Requis. Les 8 valeurs viennent de `AnalyseCriterion` (jamais recopiées à la main). |
| `scope` | `PLAYER` \| `TEAM` \| `UNKNOWN` | Requis. Doit correspondre à la portée de `criterion` ; sinon l'entrée est rejetée. |
| `alternatives` | liste de 0 à 8 identifiants du catalogue | Requis (vide sauf ambiguïté). Doublons et `NONE` interdits. |

Validation côté serveur (FR-004) : schéma strict + contrôle des identifiants et de la cohérence de portée ;
tout écart → « aucune interprétation ».

## 2. Interprétation (objet interne)

| Champ | Valeurs |
|-------|---------|
| `outcome` | `RECOGNIZED`, `AMBIGUOUS`, `NONE` |
| `criterion` | identifiant retenu (si `RECOGNIZED`) |
| `alternatives` | identifiants proposés (si `AMBIGUOUS`) |
| `source` | `KEYWORDS` ou `ASSISTANT` |

Transitions dans `AnalyseService.answer(question)` :

```text
mots-clés ── reconnue ─────────────────────────────▶ RECOGNIZED (KEYWORDS)
          └─ non reconnue / ambiguë ─▶ assistant activé et sous le plafond ?
                                         ├─ non ──────────────▶ résultat des mots-clés
                                         └─ oui ─▶ appel (3 s)
                                                  ├─ critère valide ─▶ RECOGNIZED (ASSISTANT)
                                                  ├─ ambiguïté ──────▶ AMBIGUOUS (ASSISTANT)
                                                  └─ NONE / échec ───▶ résultat des mots-clés
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
