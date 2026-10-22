# Implementation Plan: Tool Claude rank(criterion, scope)

**Branch**: `initDonneesNotation` (branche unique ; dossier de fonctionnalité `002-tool-claude-rank`) | **Date**: 2026-10-21 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-tool-claude-rank/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Quand la reconnaissance par mots-clés de l'onglet Analyse ne reconnaît pas une question libre ou la juge
ambiguë, le backend consulte Claude Haiku 5.5 avec un unique tool `rank(criterion, scope)` forcé et à schéma
strict : le modèle ne peut que choisir un identifiant du catalogue existant, déclarer une ambiguïté ou
répondre `NONE`. Le serveur valide ce choix puis calcule la réponse avec le même SQL qu'une suggestion.
Délai de 3 s sans nouvelle tentative, plafond de 30 appels par minute, repli silencieux sur les mots-clés,
clé `ANTHROPIC_API_KEY` côté serveur, aucune donnée hors la question transmise. L'interface affiche comment
la question a été comprise et, si l'assistant est configuré, une mention de transmission. Détails :
[research.md](research.md).

## Technical Context

**Language/Version**: Java 25 (backend), TypeScript 5.9 / Angular 20 (frontend)

**Primary Dependencies**: Spring Boot 3.5 ; **nouvelle** : SDK officiel `com.anthropic:anthropic-java` 2.34.0
(client `AnthropicOkHttpClient`)

**Storage**: aucun nouveau stockage (PostgreSQL inchangé, aucune migration)

**Testing**: JUnit 5 + AssertJ ; MockMvc ; tests unitaires de l'interprète sans réseau ; évaluation réelle
optionnelle (`AssistantEvalTest`, désactivée par défaut)

**Target Platform**: même JAR ; appel sortant HTTPS vers l'API Claude (`api.anthropic.com`)

**Project Type**: application web (monolithe Maven `frontend` + `backend`)

**Performance Goals**: question libre interprétée en moins de 3 s pour 95 % des cas (SC-004) ; questions
reconnues par mots-clés sans délai ajouté

**Constraints**: modèle `claude-haiku-5-5` ; `tool_choice` forcé sur `rank`, `strict: true` ; aucun paramètre
d'échantillonnage (400 sur Haiku 5.5) ; délai 3 s, `maxRetries` 0 ; 30 appels / 60 s ; question jamais
journalisée sauf par la trace des échanges (désactivable, ANALYSE_TRACE=false) ; application fonctionnelle sans clé

**Scale/Scope**: 1 tool, 1 appel par question non reconnue, 2 routes touchées (`/api/analyse?q=`, nouvelle
`/api/analyse/assistant`), 1 écran modifié (Analyse)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Vérification | Avant Phase 0 | Après Phase 1 |
|----------|--------------|---------------|---------------|
| I. Flyway + donneesStats.sql | aucun changement de schéma, aucune migration | ✅ | ✅ |
| II. Reproductible | notes et calculs inchangés ; l'interprétation n'influe que sur le choix de la question, jamais sur les chiffres | ✅ | ✅ |
| III. Tests PostgreSQL | les calculs restent couverts par les tests existants ; nouveaux tests unitaires de l'interprète et de l'orchestration ; aucun mock du SGBD | ✅ | ✅ |
| IV. API = contrat | ajout compatible `interpretation` dans `AnalyseAnswer` ; nouvelle route `GET /api/analyse/assistant` testée et ajoutée au README | ✅ | ✅ |
| V. Simplicité, branche unique | seule dépendance ajoutée : le SDK Anthropic, explicitement prévu par la constitution ; plafond et délai sans bibliothèque ; commits sur `initDonneesNotation` | ✅ | ✅ |
| VI. Origine des données | aucune nouvelle donnée ; les mentions « données fictives » existantes sont conservées | ✅ | ✅ |
| VII. Chiffres du SQL | Amendement 2 du 2026-10-22 (constitution 2.2.0) : le modèle appelle des tools de lecture exécutés par l'application (classements en SQL) ; le modèle ne choisit qu'une statistique d'une liste fermée et un sens de tri (énumération stricte ; Amendement du 2026-10-22 (constitution 2.1.0), rank sans critères) ; pour une question du catalogue, aucun classement ne vient du modèle (mode libre hors catalogue : voir la constitution 2.0.0, réponse courte étiquetée, calculs permis, tracée) ; fonctionne sans clé (mots-clés) ; clé côté serveur en variable d'environnement | ✅ | ✅ |

Aucune dérogation : la section Complexity Tracking est vide.

## Project Structure

### Documentation (this feature)

```text
specs/002-tool-claude-rank/
├── spec.md
├── plan.md              # ce fichier
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   └── api.md           # Phase 1 (routes + appel Claude + configuration)
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
backend/pom.xml                                   # dépendance com.anthropic:anthropic-java 2.34.0
backend/src/main/resources/application.yml       # analyse.assistant.* (clé via ANTHROPIC_API_KEY)
backend/src/main/java/com/emocio/analysefoot/
├── analyse/
│   ├── QuestionInterpreter.java                 # port : interprète une question → Interpretation
│   ├── Interpretation.java                      # outcome, criterion, alternatives, source
│   ├── ClaudeQuestionInterpreter.java           # appel Claude Haiku 5.5, tool rank forcé et strict
│   ├── DisabledQuestionInterpreter.java         # sans clé : jamais d'interprétation
│   ├── AssistantRateLimiter.java                # 30 appels / 60 s
│   ├── AssistantConfig.java                     # propriétés analyse.assistant.*, choix de l'interprète
│   └── AnalyseService.java                      # consultation si mots-clés échouent, repli
├── controller/AnalyseController.java            # GET /api/analyse/assistant
└── dto/AnalyseAnswer.java, AnalyseInterpretation.java, AssistantStatus.java

backend/src/test/java/com/emocio/analysefoot/
├── analyse/ClaudeQuestionInterpreterTest.java   # requête construite + lecture de l'entrée du tool
├── analyse/AnalyseServiceAssistantTest.java     # orchestration avec interprète factice, plafond
├── analyse/AssistantEvalTest.java               # évaluation réelle, désactivée par défaut
└── controller/AnalyseControllerTest.java        # interpretation, /api/analyse/assistant

frontend/src/app/
├── core/models/analyse.ts                       # interpretation, AssistantStatus
├── core/services/analyse.service.ts             # getAssistantStatus()
└── features/analyse/analyse.ts                  # « question comprise comme … », mention de transmission

README.md                                        # ANTHROPIC_API_KEY, nouvelle route
```

**Structure Decision**: structure web existante. Les nouvelles classes vont dans le paquet `analyse`, déjà
dédié à la reconnaissance des questions ; l'interprète est un port (`QuestionInterpreter`) avec deux
implémentations, ce qui garde les tests hors ligne et le démarrage sans clé.

## Ordre de réalisation (commits sur initDonneesNotation)

1. Dépendance SDK, configuration, port `QuestionInterpreter`, implémentation désactivée, plafond.
2. `ClaudeQuestionInterpreter` (requête + lecture) et ses tests sans réseau – histoire P1.
3. Orchestration dans `AnalyseService` (consultation si mots-clés échouent, repli) – histoires P1 et P2.
4. `interpretation` dans la réponse, `GET /api/analyse/assistant`, écran (mention, « question comprise
   comme ») – histoire P3.
5. README ; évaluation réelle avec une clé (accord du propriétaire de la clé requis).

## Complexity Tracking

Aucune violation de la constitution à justifier.
