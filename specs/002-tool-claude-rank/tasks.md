---

description: "Tâches du tool Claude rank(criterion, scope)"
---

# Tasks: Tool Claude rank(criterion, scope)

**Input**: Design documents from `/specs/002-tool-claude-rank/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/api.md, quickstart.md

**Tests**: inclus (constitution, principe III : toute fonctionnalité touchant les critères d'analyse ou
l'API est testée). La suite par défaut reste hors ligne et gratuite ; l'évaluation réelle (payante) est
une tâche à part, désactivée par défaut.

**Organization**: tâches groupées par histoire utilisateur ; toutes sur `initDonneesNotation`
(constitution V), un commit par groupe, tests au vert (hook pre-commit : build Angular + tests backend ;
hook Claude Code : migrations protégées – aucune migration ici).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallélisable (fichiers différents, pas de dépendance ouverte)
- **[Story]**: US1 (formulations libres), US2 (sans assistant), US3 (interprétation affichée)

---

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Ajouter la dépendance `com.anthropic:anthropic-java` version `2.34.0` dans backend/pom.xml (seule dépendance nouvelle, prévue par la constitution V) et vérifier `./mvnw -pl backend dependency:tree` sans conflit
- [X] T002 [P] Ajouter dans backend/src/main/resources/application.yml le bloc `analyse.assistant` : `api-key: ${ANTHROPIC_API_KEY:}`, `model: claude-haiku-5-5`, `timeout: 3s`, `max-per-minute: 30` (contracts/api.md §3) ; aucune valeur de clé dans le dépôt

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: le port d'interprétation, son implémentation désactivée et le plafond, utilisés par les trois histoires

- [X] T003 [P] Créer le record `Interpretation(Outcome outcome, AnalyseCriterion criterion, List<AnalyseCriterion> alternatives, Source source)` avec les énumérations `Outcome { RECOGNIZED, AMBIGUOUS, NONE }` et `Source { KEYWORDS, ASSISTANT }`, et les fabriques `none()`, `recognized(c, source)`, `ambiguous(list, source)` dans backend/src/main/java/com/emocio/analysefoot/analyse/Interpretation.java (data-model §2)
- [X] T004 [P] Créer l'interface `QuestionInterpreter` (`Interpretation interpret(String question)` ; `boolean enabled()`) dans backend/src/main/java/com/emocio/analysefoot/analyse/QuestionInterpreter.java, avec la règle documentée : ne jamais lever d'exception, renvoyer `Interpretation.none()` en cas de doute
- [X] T005 [P] Créer `DisabledQuestionInterpreter` (`enabled()` = false, `interpret` = `Interpretation.none()`) dans backend/src/main/java/com/emocio/analysefoot/analyse/DisabledQuestionInterpreter.java
- [X] T006 [P] Créer `AssistantRateLimiter` (fenêtre glissante de 60 s, au plus `max-per-minute` = 30 appels pour toute l'application, `boolean tryAcquire()` thread-safe, horloge `java.time.Clock` injectée pour les tests) dans backend/src/main/java/com/emocio/analysefoot/analyse/AssistantRateLimiter.java (research R6) et son test backend/src/test/java/com/emocio/analysefoot/analyse/AssistantRateLimiterTest.java (30 acceptés, 31e refusé, accepté de nouveau après 60 s)
- [X] T007 Créer `AssistantConfig` (`@ConfigurationProperties("analyse.assistant")` + `@Configuration`) dans backend/src/main/java/com/emocio/analysefoot/analyse/AssistantConfig.java : bean `QuestionInterpreter` = `DisabledQuestionInterpreter` si la clé est vide, sinon `ClaudeQuestionInterpreter` (créé en Phase 3) avec un `AnthropicClient` construit par `AnthropicOkHttpClient.builder().apiKey(...).timeout(Duration)…maxRetries(0)` ; bean `AssistantRateLimiter` ; tant que T010 n'existe pas, ne déclarer que la branche désactivée (depends on T002, T005, T006)
- [X] T008 Vérifier que l'application et tous les tests démarrent sans `ANTHROPIC_API_KEY` (`./mvnw -pl backend test`) : l'interprète est désactivé (depends on T007)

**Checkpoint**: commit « Assistant : configuration, port d'interprétation, plafond »

---

## Phase 3: User Story 1 - Comprendre des formulations libres (Priority: P1) 🎯 MVP

**Goal**: une question non reconnue ou ambiguë pour les mots-clés est interprétée par Claude Haiku 5.5 via le tool `rank`, puis reçoit la même réponse que la suggestion correspondante

**Independent Test**: avec une clé, les formulations de quickstart.md §3 (« quelle nation a planté le plus ? », « meilleur buteure du mondiale », « quel est le salaire de Mbappé ? ») donnent la réponse attendue ; sans réseau, les tests T009 et T012 passent

### Tests for User Story 1

- [X] T009 [P] [US1] Créer backend/src/test/java/com/emocio/analysefoot/analyse/ClaudeQuestionInterpreterTest.java (sans réseau) : (a) la requête construite par `buildRequest(question)` a le modèle `claude-haiku-5-5`, `max_tokens` 1024, un seul tool `rank` avec `strict: true` et le schéma de contracts/api.md §2 (énumération `criterion` = les 8 identifiants de `AnalyseCriterion` + `NONE`, `scope` ∈ `PLAYER`/`TEAM`/`UNKNOWN`, `alternatives` sans `NONE`, `additionalProperties: false`, les 3 champs requis), `tool_choice` forcé sur `rank`, aucun `temperature`/`top_p`/`top_k`/`thinking`, et un seul message utilisateur égal à la question ; (b) `parseInput(json)` : identifiant valide et portée cohérente → `RECOGNIZED` (source `ASSISTANT`) ; `NONE` → `NONE` ; `alternatives` de 2 identifiants → `AMBIGUOUS` ; identifiant inconnu, portée incohérente (ex. `TOP_SCORER` + `TEAM`), `NONE` dans `alternatives` ou JSON illisible → `NONE`
- [X] T010 [P] [US1] Créer backend/src/test/java/com/emocio/analysefoot/analyse/AnalyseServiceAssistantTest.java avec un `QuestionInterpreter` factice qui compte ses appels : question reconnue par mots-clés (« qui a pris le plus de jaunes ») → 0 appel ; non reconnue (« quelle nation a planté le plus ? ») + interprète qui renvoie `TOP_SCORING_TEAM` → réponse `TOP_SCORING_TEAM` ; ambiguë (« le moins fair-play ») + interprète `AMBIGUOUS` → `status` `AMBIGUOUS` et `candidates` de l'interprète ; interprète `NONE` → résultat des mots-clés inchangé

### Implementation for User Story 1

- [X] T011 [US1] Créer `ClaudeQuestionInterpreter` dans backend/src/main/java/com/emocio/analysefoot/analyse/ClaudeQuestionInterpreter.java : `buildRequest(question)` (non beta : `com.anthropic.models.messages.MessageCreateParams`, `Tool` avec `.strict(true)` et `Tool.InputSchema` construit depuis `AnalyseCriterion.values()`, `.putAdditionalProperty("additionalProperties", JsonValue.from(false))`, `ToolChoiceTool` sur `rank`, prompt système fixe listant les 8 questions – identifiant, libellé, portée, ce qu'elles mesurent – et la règle `NONE` hors catalogue / `alternatives` en cas d'ambiguïté) ; `interpret(question)` : appel `client.messages().create(...)`, `stop_reason` doit être `tool_use`, lecture du bloc `tool_use` nommé `rank` via `parseInput` ; toute exception du SDK, `refusal`, `max_tokens` ou entrée invalide → `Interpretation.none()` ; journaliser le type d'erreur seulement, jamais la question (FR-007, research R2–R4, R8) (depends on T003, T004)
- [X] T012 [US1] Activer dans backend/src/main/java/com/emocio/analysefoot/analyse/AssistantConfig.java la branche « clé présente » qui crée `ClaudeQuestionInterpreter` (depends on T007, T011)
- [X] T013 [US1] Dans backend/src/main/java/com/emocio/analysefoot/analyse/AnalyseService.java, injecter `QuestionInterpreter` et `AssistantRateLimiter` ; dans `answer(String)` : si `QuestionMatcher` reconnaît la question, répondre sans consulter l'assistant ; sinon, si l'interprète est activé et `tryAcquire()` réussit, l'appeler : `RECOGNIZED` → `answer(criterion)` ; `AMBIGUOUS` → `AMBIGUOUS` avec ses `alternatives` ; `NONE` → résultat des mots-clés (FR-003, research R5) (depends on T010, T011)

**Checkpoint**: US1 démontrable avec une clé — commit « Assistant : tool rank et interprétation des questions libres »

---

## Phase 4: User Story 2 - Continuer à fonctionner sans l'assistant (Priority: P2)

**Goal**: sans clé, en panne, trop lent ou au-delà du plafond, la reconnaissance par mots-clés s'applique sans message d'erreur

**Independent Test**: tests T014 et T015 ; quickstart.md §2 (sans clé) et §3 étape 3 (réseau coupé)

### Tests for User Story 2

- [X] T014 [P] [US2] Ajouter à backend/src/test/java/com/emocio/analysefoot/analyse/AnalyseServiceAssistantTest.java : interprète désactivé → aucune consultation et réponses identiques aux mots-clés pour toutes les reformulations de `QuestionMatcherTest` ; interprète qui lève une exception → résultat des mots-clés, aucune exception propagée ; plafond atteint (`AssistantRateLimiter` à 0 restant) → aucune consultation
- [X] T015 [P] [US2] Ajouter à backend/src/test/java/com/emocio/analysefoot/analyse/ClaudeQuestionInterpreterTest.java : un `AnthropicClient` dont l'appel lève `AnthropicServiceException` (ou une erreur réseau) ou dépasse le délai, et une réponse `stop_reason` `refusal`, donnent `Interpretation.none()` ; le client est construit avec `maxRetries(0)` et le délai configuré (3 s)

### Implementation for User Story 2

- [X] T016 [US2] Dans backend/src/main/java/com/emocio/analysefoot/analyse/AnalyseService.java, protéger l'appel à l'interprète par un `try/catch (RuntimeException)` qui revient aux mots-clés (défense en profondeur, en plus de la règle du port) (depends on T013, T014)

**Checkpoint**: US1 et US2 fonctionnent — commit « Assistant : repli sur les mots-clés »

---

## Phase 5: User Story 3 - Savoir comment la question a été comprise (Priority: P3)

**Goal**: la réponse indique la question retenue et l'origine (mots-clés ou assistant) ; la mention de transmission n'apparaît que si l'assistant est configuré

**Independent Test**: tests T017 et T018 ; quickstart.md §2 étape 2 et §3 étape 2

### Tests for User Story 3

- [X] T017 [P] [US3] Ajouter à backend/src/test/java/com/emocio/analysefoot/controller/AnalyseControllerTest.java : `GET /api/analyse?q=` renvoie `interpretation.source` (`KEYWORDS` ou `ASSISTANT`) et `interpretation.understoodAs` ; `GET /api/analyse/{questionId}` renvoie `interpretation` null ; `GET /api/analyse/assistant` renvoie `{"enabled": true}` ou `{"enabled": false}` selon l'interprète (contracts/api.md §1)
- [X] T018 [P] [US3] Ajouter à backend/src/test/java/com/emocio/analysefoot/analyse/AnalyseServiceAssistantTest.java : `interpretation.source` = `KEYWORDS` pour une question reconnue par mots-clés, `ASSISTANT` pour une question interprétée par l'assistant, et `understoodAs` = libellé de la question retenue

### Implementation for User Story 3

- [X] T019 [P] [US3] Créer les records `AnalyseInterpretation(String source, String understoodAs)` et `AssistantStatus(boolean enabled)` dans backend/src/main/java/com/emocio/analysefoot/dto/, et ajouter le composant `AnalyseInterpretation interpretation` à backend/src/main/java/com/emocio/analysefoot/dto/AnalyseAnswer.java (ajout compatible ; null pour une suggestion cliquée) ; adapter les constructions existantes de `AnalyseAnswer` (service et tests)
- [X] T020 [US3] Renseigner `interpretation` dans `AnalyseService.answer(String)` (backend/src/main/java/com/emocio/analysefoot/analyse/AnalyseService.java) et ajouter `GET /api/analyse/assistant` dans backend/src/main/java/com/emocio/analysefoot/controller/AnalyseController.java (depends on T013, T019)
- [X] T021 [P] [US3] Ajouter `AnalyseInterpretation`, `AssistantStatus` et `interpretation` à frontend/src/app/core/models/analyse.ts, et `getAssistantStatus()` à frontend/src/app/core/services/analyse.service.ts
- [X] T022 [US3] Dans frontend/src/app/features/analyse/analyse.ts : afficher sous la réponse « Question comprise comme : <libellé> (mots-clés | assistant) » quand `interpretation` est présent ; afficher sous la barre de recherche, seulement si `enabled`, la mention « Une question non reconnue peut être transmise à Anthropic (Claude) pour être interprétée. » (FR-006, FR-008) (depends on T021)

**Checkpoint**: les trois histoires fonctionnent — commit « Assistant : interprétation affichée »

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T023 [P] Mettre à jour README.md : variable `ANTHROPIC_API_KEY` (côté serveur, jamais commitée, assistant désactivé sans elle), route `GET /api/analyse/assistant`, champ `interpretation`, données transmises à Anthropic (la question seule)
- [X] T024 [P] Mettre à jour backend/src/main/resources/db/migration/cadrage.txt : étape 5 (Tool Claude) réalisée, choix Q1–Q3 de `specs/002-tool-claude-rank`
- [X] T025 Créer backend/src/test/java/com/emocio/analysefoot/analyse/AssistantEvalTest.java, désactivé par défaut (`@EnabledIfSystemProperty(named = "analyse.assistant.eval", matches = "true")` et clé présente) : 40 formulations non reconnues par les mots-clés (5 par question) et 10 pièges hors catalogue ; attendu ≥ 36/40 bonnes questions (SC-001) et 10/10 pièges non reconnus (SC-002) ; vérifier d'abord que chaque formulation n'est PAS reconnue par `QuestionMatcher`
- [X] T026 Lancer `./mvnw -pl backend test` puis `./mvnw package` (build Angular compris) sans clé ; corriger toute erreur
- [ ] T027 Avec l'accord du propriétaire de la clé : lancer `AssistantEvalTest` (50 requêtes, moins d'un centime) et dérouler quickstart.md §3 sur http://localhost:8080 avec `ANTHROPIC_API_KEY` ; reporter les scores SC-001/SC-002 et le temps de réponse (SC-004) dans quickstart.md

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** : aucune dépendance.
- **Foundational (Phase 2)** : après Setup ; bloque les histoires (port, plafond, configuration).
- **US1 (Phase 3)** → **US2 (Phase 4)** → **US3 (Phase 5)** : US2 et US3 modifient `AnalyseService` après US1.
- **Polish (Phase 6)** : après les histoires ; T027 exige une clé et l'accord de son propriétaire.

### User Story Dependencies

- **US1 (P1)** : après Foundational.
- **US2 (P2)** : ses tests (T014, T015) peuvent s'écrire dès la fin de Foundational ; T016 suit T013.
- **US3 (P3)** : T019 et T021 indépendants ; T020 suit T013 ; T022 suit T021.

### Within Each User Story

- Tests d'abord (ils échouent), puis implémentation.
- Fichiers partagés modifiés en séquence : `AnalyseService.java` (T013 → T016 → T020), `AssistantConfig.java` (T007 → T012), `AnalyseServiceAssistantTest.java` (T010 → T014 → T018), `ClaudeQuestionInterpreterTest.java` (T009 → T015), `AnalyseControllerTest.java` (T017), `analyse.ts` (T022).

### Parallel Opportunities

- Phase 1 : T002 en parallèle de T001.
- Phase 2 : T003 ∥ T004 ∥ T005 ∥ T006.
- US1 : T009 ∥ T010.
- US2 : T014 ∥ T015 (fichiers différents).
- US3 : T017 ∥ T018 ∥ T019 ∥ T021.
- Polish : T023 ∥ T024.

---

## Parallel Example: User Story 1

```bash
Task: "Tests de la requête et de la lecture du tool dans backend/src/test/java/com/emocio/analysefoot/analyse/ClaudeQuestionInterpreterTest.java"
Task: "Tests de l'orchestration dans backend/src/test/java/com/emocio/analysefoot/analyse/AnalyseServiceAssistantTest.java"
```

## Parallel Example: Foundational

```bash
Task: "Record Interpretation dans backend/src/main/java/com/emocio/analysefoot/analyse/Interpretation.java"
Task: "Interface QuestionInterpreter dans backend/src/main/java/com/emocio/analysefoot/analyse/QuestionInterpreter.java"
Task: "DisabledQuestionInterpreter dans backend/src/main/java/com/emocio/analysefoot/analyse/DisabledQuestionInterpreter.java"
Task: "AssistantRateLimiter et son test"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phases 1 et 2 : l'application tourne sans clé, à l'identique.
2. Phase 3 : le tool `rank` interprète les questions non reconnues.
3. **STOP and VALIDATE** : tests hors ligne verts ; avec une clé, les 3 formulations de quickstart.md §3.
4. Commit sur `initDonneesNotation`.

### Incremental Delivery

1. Fondations → commit (aucun changement visible).
2. US1 → commit (MVP, avec clé).
3. US2 → commit (robustesse sans clé, en panne, plafond).
4. US3 → commit (transparence à l'écran).
5. Polish → README, cadrage, évaluation réelle avec accord.

---

## Notes

- Aucune migration, aucun changement de `donneesStats.sql` (le hook PreToolUse sur les migrations n'est pas sollicité).
- Pour une question du catalogue, le modèle ne choisit qu'un identifiant et le classement vient du SQL ; le mode
  libre (amendement du 2026-10-22 (constitution 2.0.0), constitution VII) peut rédiger une réponse courte étiquetée, calculs permis.
- Aucune requête payante dans la suite de tests par défaut.
