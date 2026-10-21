# Phase 0 – Recherche : Tool Claude rank(criterion, scope)

Références : [spec.md](spec.md), constitution v1.1.0 (principes V et VII), `specs/001-onglet-analyse`
(catalogue `AnalyseCriterion`, `QuestionMatcher`, `AnalyseService`). Sources API : skill `claude-api`
(référence Java et guide de migration, version mise en cache le 06/10/2026).

## R1. Modèle et SDK

- **Decision** : SDK officiel `com.anthropic:anthropic-java` (2.34.0) ; modèle `claude-haiku-5-5`
  (identifiant exact, sans suffixe de date) ; client `AnthropicOkHttpClient` construit avec la clé
  `ANTHROPIC_API_KEY`.
- **Rationale** : la spécification impose Claude Haiku (clarification Q3) ; Claude Haiku 5.5 est la
  génération actuelle, faite pour la classification et le routage à fort volume, au tarif le plus bas
  ($0,10 / $0,50 par million de jetons en dessous de 100 000 jetons de prompt). La constitution (V) prévoit
  le SDK Anthropic comme seule dépendance externe.
- **Alternatives considered** : Claude Haiku 4.5 (génération précédente, plus chère) ; appels HTTP bruts
  (le skill impose le SDK officiel quand il existe) ; proxy compatible OpenAI de `LLM_KEY` (écarté par Q3).

## R2. Forme de l'appel : un tool forcé, à schéma strict

- **Decision** : un seul tool client `rank`, déclaré avec `strict: true`, et `tool_choice` forcé sur ce
  tool (`{type: "tool", name: "rank"}`). Schéma d'entrée (`additionalProperties: false`, tous champs
  requis) :
  - `criterion` : énumération des 8 identifiants du catalogue + `NONE` ;
  - `scope` : `PLAYER` | `TEAM` | `UNKNOWN` ;
  - `alternatives` : liste (0 à 8) d'identifiants du catalogue, renseignée seulement en cas d'ambiguïté.
  Aucun résultat de tool n'est renvoyé au modèle : un seul aller (une requête, une réponse), sans boucle.
- **Rationale** : Claude Haiku 5.5 accepte le `tool_choice` forcé (contrairement à Opus 5.5 / Sonnet 5.5) ;
  la réponse commence alors directement par l'appel du tool, sans bloc de réflexion : rapide et peu coûteux.
  L'énumération stricte borne la sortie au catalogue (FR-001, principe VII) et limite l'effet d'une
  injection d'instructions : le modèle ne peut que choisir un identifiant.
- **Alternatives considered** : `tool_choice: auto` + consigne (le modèle pourrait répondre en texte) ;
  sorties structurées `output_config.format` (équivalent, mais le cadrage et la demande nomment le tool
  `rank(criterion, scope)`) ; Tool Runner (boucle inutile pour un choix unique).

## R3. Paramètres de requête

- **Decision** : `max_tokens` = 1 024 ; pas de `temperature`, `top_p` ni `top_k` ; pas de paramètre
  `thinking` ni `effort` (le tool forcé saute la réflexion) ; prompt système fixe décrivant les 8 questions
  (libellé, portée, ce qu'elles mesurent) et la règle « ne choisis qu'un identifiant, NONE si la question
  sort du catalogue » ; message utilisateur = la question seule.
- **Rationale** : Claude Haiku 5.5 renvoie une erreur 400 sur toute valeur d'échantillonnage non par défaut ;
  la réflexion compte dans `max_tokens` (garder de la marge) ; seule la question est transmise (FR-007).
- **Alternatives considered** : mise en cache du prompt système (préfixe trop court pour être mis en cache :
  sans effet) ; few-shot (inutile à ce stade, à mesurer si SC-001 n'est pas atteint).

## R4. Délai, erreurs, refus

- **Decision** : délai de requête 3 s (`timeout` du client), aucune nouvelle tentative (`maxRetries` 0) ;
  toute erreur du SDK (`AnthropicServiceException`, erreurs réseau), `stop_reason` autre que `tool_use`
  (dont `refusal` : les filtres de sécurité de Claude Haiku 5.5 n'ont pas de repli côté serveur), bloc
  `tool_use` absent ou entrée invalide (identifiant inconnu, portée incohérente) → interprétation ignorée,
  repli sur les mots-clés (FR-004, FR-005).
- **Rationale** : les nouvelles tentatives par défaut (2) multiplieraient le délai au-delà de SC-003 ;
  le visiteur ne doit jamais voir d'erreur technique.
- **Alternatives considered** : modèle de repli en cas de refus (non disponible pour Haiku 5.5, et inutile :
  les mots-clés sont le repli).

## R5. Quand consulter l'assistant (FR-003)

- **Decision** : `AnalyseService.answer(String)` applique d'abord `QuestionMatcher` ; il ne consulte
  l'assistant que si le résultat est « non reconnue » ou « ambiguë ». Si l'assistant tranche, sa question
  est retenue ; s'il déclare une ambiguïté, les `alternatives` sont proposées (à défaut, celles des
  mots-clés) ; s'il répond `NONE` ou échoue, le résultat des mots-clés s'applique.
- **Rationale** : clarification Q1 ; coût et envoi de données réduits au minimum ; aucune régression des
  questions reconnues aujourd'hui (SC-003).

## R6. Plafond d'usage (FR-011)

- **Decision** : seau à jetons en mémoire, 30 consultations par fenêtre glissante de 60 s pour toute
  l'application (une seule instance) ; au-delà, pas d'appel.
- **Rationale** : protège le coût sans dépendance (constitution V : pas de bibliothèque de limitation).
- **Alternatives considered** : Bucket4j / Resilience4j (dépendances non justifiées pour un compteur).

## R7. Configuration et absence de clé

- **Decision** : propriétés `analyse.assistant.api-key` (`${ANTHROPIC_API_KEY:}`), `analyse.assistant.model`
  (`claude-haiku-5-5`), `analyse.assistant.timeout` (`3s`), `analyse.assistant.max-per-minute` (`30`).
  Clé vide → aucun client créé ; l'interprète n'est qu'un « non configuré » qui renvoie toujours
  « aucune interprétation ». La route `GET /api/analyse/assistant` indique `{ "enabled": true|false }`
  pour afficher la mention de FR-008.
- **Rationale** : FR-005, FR-009 ; l'application démarre et passe ses tests sans clé. Spring ne lit pas
  `.env` sur cette branche : la clé est une variable d'environnement.

## R8. Confidentialité (FR-007, FR-008)

- **Decision** : la question n'est ni journalisée ni conservée ; les journaux d'erreur citent le type
  d'erreur, jamais la question. Mention permanente sous la barre de recherche, seulement si
  `enabled` : « Une question non reconnue peut être transmise à Anthropic (Claude) pour être interprétée. »

## R9. Tests et évaluation

- **Decision** :
  - tests unitaires de `ClaudeQuestionInterpreter` sans réseau : construction de la requête (modèle, tool
    strict, `tool_choice`, aucune donnée autre que la question) et lecture de l'entrée du tool à partir de
    JSON (identifiant valide, `NONE`, ambiguïté, identifiant inconnu, portée incohérente) ;
  - tests de `AnalyseService` avec un interprète factice : consultation seulement si les mots-clés
    échouent, repli en cas d'échec, origine `KEYWORDS` / `ASSISTANT`, plafond ;
  - évaluation réelle (SC-001, SC-002) : test JUnit désactivé par défaut, lancé seulement avec une clé et
    `-Danalyse.assistant.eval=true` sur 40 formulations + 10 pièges ; il coûte de vraies requêtes, à lancer
    avec l'accord du propriétaire de la clé.
- **Rationale** : la suite par défaut reste gratuite, hors ligne et déterministe (hook pre-commit) ; la
  qualité du modèle est mesurée à part.
