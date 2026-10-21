# Quickstart – valider le tool Claude rank(criterion, scope)

Contrat : [contracts/api.md](contracts/api.md) ; modèle : [data-model.md](data-model.md).

## Prérequis

- Branche `initDonneesNotation`, onglet Analyse (`specs/001-onglet-analyse`) en place, base `analysefoot`
  à jour.
- Pour les étapes 3 et 4 : une clé API Anthropic dans la variable `ANTHROPIC_API_KEY` (jamais commitée).

## 1. Tests automatisés (sans clé, sans réseau)

```bash
./mvnw -pl backend test
```

Attendu : tous les tests passent, dont ceux qui vérifient la requête construite (modèle `claude-haiku-5-5`,
tool `rank` strict et forcé, seule la question transmise), la lecture de l'entrée du tool, la consultation
seulement si les mots-clés échouent, le repli en cas d'échec et le plafond de 30 appels par minute.

## 2. Sans assistant (SC-003)

1. Lancer l'application sans `ANTHROPIC_API_KEY`.
2. `GET /api/analyse/assistant` → `{"enabled": false}` ; aucune mention sous la barre de recherche.
3. Les reformulations reconnues aujourd'hui (tests `QuestionMatcherTest`) donnent la même réponse, avec
   `interpretation.source` = `KEYWORDS`.

## 3. Avec assistant (spec US1, US3)

1. Lancer l'application avec `ANTHROPIC_API_KEY`.
2. `GET /api/analyse/assistant` → `{"enabled": true}` ; la mention de transmission est visible.

| Question saisie | Attendu |
|-----------------|---------|
| « quelle nation a planté le plus ? » | France (Équipe qui a marqué le plus de buts), compris par l'assistant |
| « meilleur buteure du mondiale » | Kylian Mbappé (Meilleur buteur), compris par l'assistant |
| « quel est le salaire de Mbappé ? » | question non reconnue, 8 questions proposées |
| « qui a pris le plus de jaunes » | Issa Diop, compris par les mots-clés (aucun appel) |
| « le moins fair-play » | assistant consulté ; à défaut de choix, 2 questions proposées |

3. Couper le réseau (ou mettre une clé invalide) : les mêmes questions reconnues par mots-clés répondent
   toujours, sans message d'erreur.

## 4. Évaluation réelle (SC-001, SC-002) – payante

```bash
ANTHROPIC_API_KEY=... ./mvnw -pl backend test -Dtest=AssistantEvalTest -Danalyse.assistant.eval=true
```

50 requêtes Claude Haiku 5.5 (40 formulations + 10 pièges), soit environ 50 × 700 jetons d'entrée et
50 × 60 jetons de sortie : moins d'un centime au tarif de Claude Haiku 5.5 ($0,10 / $0,50 par million de
jetons) ; à lancer avec l'accord du propriétaire de la clé. Attendu : au moins 36/40 bonnes questions, 10/10 pièges non
reconnus.
