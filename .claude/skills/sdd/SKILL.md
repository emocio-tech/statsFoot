---
name: sdd
description: Développe une feature en Spec Driven Development - écrit une spec versionnée (specs/<feature>/), puis un plan technique, puis des tâches, avec validation humaine à chaque étape, avant d'implémenter et de vérifier la conformité à la spec. À utiliser pour « fais une spec pour… », « SDD », « spécifie la feature X », « implémente à partir de la spec ».
---

# Spec Driven Development

La **spec est la source de vérité** : le code en découle, et on ne code rien qui ne soit pas dans la spec. Chaque étape produit un fichier versionné et **s'arrête sur une validation humaine** avant la suivante.

```
specs/<feature>/
  spec.md     # QUOI et POURQUOI (aucune technique)
  plan.md     # COMMENT (architecture, choix techniques)
  tasks.md    # tâches ordonnées et vérifiables
```

`<feature>` est en `kebab-case`, en anglais. Les fichiers de `specs/` sont rédigés **en anglais** (convention du projet). Les échanges avec l'utilisateur restent en français.

## Étape 0 : Démarrer

1. Reformule la demande en une phrase et demande confirmation si elle est floue.
2. Si `specs/<feature>/` existe déjà, lis-le et demande : reprendre, amender la spec ou repartir de zéro. Ne l'écrase jamais sans accord.
3. Lis `CLAUDE.md` : respecte le périmètre du projet (Coupe du Monde 2026 uniquement, pas de comparaison inter-championnats ou inter-années) et l'architecture (Spring Boot, Angular, données JSON dans `data/`).
4. Propose la branche `fonc/<feature>` (convention du projet) et ne la crée qu'après accord.

## Étape 1 : Spécifier → `spec.md`

Ne parle ni de classes, ni de framework, ni de fichiers. Pose les questions au fil de l'eau (3 à 5 à la fois, pas un questionnaire de 20 lignes), puis rédige :

```markdown
# <Feature title>

## Context and goal
Why this feature exists, who it is for (cf. the user persona in CLAUDE.md).

## User stories
- As a <user>, I want <capability> so that <benefit>.

## Functional requirements
- FR-1: The system must ...        (numbered, testable, one idea each)

## Acceptance criteria
- AC-1 (FR-1): Given ... When ... Then ...     (each one traces to a FR)

## Out of scope
- ...

## Open questions
- [NEEDS CLARIFICATION] ...
```

Règles :
- Chaque exigence est **numérotée et testable** ; chaque critère d'acceptation renvoie à une exigence.
- Ne devine pas : toute ambiguïté devient une question à l'utilisateur ou une entrée `[NEEDS CLARIFICATION]`.
- La section « Out of scope » est obligatoire, elle s'appuie sur le hors-périmètre de `CLAUDE.md`.

**🛑 Validation 1** : présente la spec, liste les `[NEEDS CLARIFICATION]` restants et attends le feu vert. Ne passe pas à l'étape 2 tant qu'il en reste.

## Étape 2 : Planifier → `plan.md`

1. Délègue l'exploration du code existant à l'agent `explorateur` : ce qui existe déjà, ce qui est réutilisable.
2. Si du code existant, une signature publique ou le format des JSON de `data/` est modifié, lance l'agent `analyse-impact`.
3. Rédige :

```markdown
# Plan: <Feature title>

## Approach
Summary in a few lines and the main trade-off.

## Components
Backend / Frontend / data: what is created or changed (files, classes, endpoints, JSON fields).

## Data model and contracts
Request/response shapes, JSON schema changes.

## Spec traceability
| Requirement | Covered by |
|---|---|
| FR-1 | ... |

## Risks and alternatives rejected
```

Vérifie que **chaque FR de la spec apparaît dans la table de traçabilité**. Si le plan oblige à contredire ou à compléter la spec, corrige d'abord la spec (et fais-la revalider), pas l'inverse.

**🛑 Validation 2** : présente le plan et attends le feu vert.

## Étape 3 : Découper → `tasks.md`

Délègue à l'agent `redacteur-plan-test` la rédaction des cas de test dérivés des critères d'acceptation, puis écris :

```markdown
# Tasks: <Feature title>

- [ ] T1 (FR-1, AC-1) Short imperative title
  - Files: ...
  - Done when: observable check (test name, command, behaviour)
```

Règles : tâches **petites** (une session de travail), ordonnées par dépendance, test avant ou avec le code, chacune rattachée à une FR/AC et avec un critère « done when » vérifiable. Une tâche sans FR associée est suspecte : supprime-la ou ajoute l'exigence manquante dans la spec.

**🛑 Validation 3** : présente les tâches et attends le feu vert.

## Étape 4 : Implémenter

1. Délègue à l'agent `implementeur` : une tâche à la fois, dans l'ordre de `tasks.md`.
2. Après chaque tâche : lance les vérifications (`./mvnw test` côté backend, `npm test` côté frontend quand ils existent) et coche la case dans `tasks.md`.
3. **Écart** : si l'implémentation révèle une spec fausse ou incomplète, **arrête-toi**, propose l'amendement de `spec.md` (ou `plan.md`), obtiens la validation, puis continue. Ne laisse jamais le code diverger silencieusement de la spec.

## Étape 5 : Vérifier la conformité

1. Lance l'agent `relecteur` sur le diff **avec la spec en référence** : il doit contrôler que chaque FR est implémentée et chaque AC couvert par un test.
2. Produis un tableau de conformité :

| Requirement | AC | Test / preuve | Statut |
|---|---|---|---|
| FR-1 | AC-1 | `XxxTest#nom` | ✅ / ❌ / ⚠️ |

3. Tout ❌ ou ⚠️ retourne à l'étape 4 (ou à la spec s'il s'agit d'un manque d'exigence).
4. Si le comportement ou l'API publique change, mets à jour la doc (skill `doc` ou agent `relecteur-doc`).

Termine en proposant le commit (skill `commit`) ; ne commite ni ne pousse sans demande explicite.

## Règles générales

- **Ne saute jamais une validation.** « Continue » ou « ok » vaut accord pour l'étape courante uniquement.
- **La spec d'abord** : toute demande de changement de comportement en cours de route passe par une modification de `spec.md`, puis du plan et des tâches impactés.
- **Spec courte** : si elle dépasse ~2 pages, propose de découper en plusieurs features.
- Pour un correctif trivial (faute de frappe, renommage), dis que le SDD est disproportionné et propose de travailler directement.
