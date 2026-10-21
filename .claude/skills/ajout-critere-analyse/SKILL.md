---
name: ajout-critere-analyse
description: Ajoute une question (un critère) au catalogue de l'onglet Analyse, de bout en bout : règle et départage, vues SQL et migration Flyway si besoin, entrée du catalogue, tool rank, suggestion de l'interface et tests. À utiliser quand on veut qu'un visiteur puisse poser une nouvelle question de classement (ex. « le joueur qui a le plus couru »).
---

# Ajouter une question à l'onglet Analyse

Le catalogue est fermé : un visiteur ne peut poser que les questions de l'enum `AnalyseCriterion`. Le tool `rank` ne fait que choisir une de ces entrées ; les chiffres viennent toujours du SQL (constitution, principes I, VI et VII). Travailler sur la branche `initDonneesNotation`.

## 1. Définir le critère

Fixer par écrit, avant tout code :
- **portée** : `PLAYER` (vue `v_player_tournament_stats`) ou `TEAM` (vue `v_team_tournament_stats`) ;
- **valeur classée**, son **unité** (« buts », « km »...) et le sens du tri ;
- **règle d'égalité** (colonne de départage, ou « sans départage ») : les ex aequo gardent le même rang (`RANK()`), le top 5 peut donc dépasser 5 lignes ;
- **population** (filtre) : qui est classé (ex. gardiens exclus, valeur > 0) ; NULL n'est jamais 0 ;
- **origine** de la donnée : réelle, calculée ou **FICTIVE** (principe VI : à signaler partout, `fictitious = true`).

## 2. Les vues suffisent-elles ?

Lister les colonnes des vues (`\d v_player_tournament_stats` / `v_team_tournament_stats`). Si la valeur y est, passer au 3.
Sinon :
1. créer une **nouvelle** migration `backend/src/main/resources/db/migration/V{n+1}__*.sql` (ne jamais modifier une migration existante : le hook `.claude/hooks/protect-migrations.sh` le bloque) ;
2. recopier la même modification dans `donneesStats.sql` (partie 5 « vues du tournoi », ou partie 6 pour des stats fictives), principe I ;
3. si le champ est fictif, l'ajouter à `fictitious_fields` des vues et à la génération (partie 6).

## 3. Entrée du catalogue et requête SQL

Dans `backend/src/main/java/com/emocio/analysefoot/analyse/AnalyseCriterion.java`, ajouter une constante (l'ordre de l'enum est l'ordre des suggestions) :
`NOM("Libellé", Scope.X, filtre, valeur, unité, tri, départage, libelléDeValeur|null, fictif, explication, mots-clés)`.
- Les fragments SQL sont des **constantes** (pas de `;`, `--`, `:`) ; la requête `RANK()` est construite par `AnalyseService.ranking`, ne pas la dupliquer.
- `explication` se termine par un point et dit la règle et le départage.
- mots-clés : minuscules, sans accents, au moins 3 ; si deux questions partagent des mots (joueur/équipe), s'appuyer sur la portée pour départager.
- Pour un critère de joueur, `AnalyseService.playerRanks` le prend automatiquement en compte dans le rapport joueur.

## 4. Tool rank et interface

Rien à écrire à la main : `RankTool.systemPrompt()` et le schéma du tool (`criterion`, `alternatives`) sont construits depuis l'enum. Vérifier seulement que `RankToolTest` passe et mettre à jour les endroits qui citent « 8 questions » : `README.md`, `specs/*/contracts/api.md`, tests qui comptent les candidats (`hasSize(8)`).
L'interface (`frontend/src/app/features/analyse/analyse.ts`) affiche les suggestions depuis `GET /api/analyse/questions` quand une question n'est pas reconnue : aucune suggestion à coder, seulement à contrôler.

## 5. Tests

- `AnalyseCriterionCatalogTest` (paramétré sur l'enum) : complétude, libellé reconnu, fragments constants.
- `QuestionMatcherTest` : une formulation libre reconnue et une ambiguïté éventuelle.
- Test d'intégration sur PostgreSQL `analysefoot_test` dans `WorldCupImportAndRatingTest` (classe imbriquée `Analyse`), sur le jeu `backend/src/test/resources/worldcup-sample.json` : le gagnant attendu, un cas d'égalité, un cas sans donnée.
- Si une migration a été ajoutée : le test `DonneesStats` qui rejoue `donneesStats.sql`.

## 6. Contrôle final

```bash
./mvnw -B -pl frontend,backend test      # Angular + tous les tests
```
Mettre à jour le README et la spec `specs/001-onglet-analyse` (FR-003, contrat d'API) si le nombre ou le sens des questions change. Relancer l'application avec `-pl frontend,backend package` (le module `backend` seul ne reconstruit pas Angular). Ne jamais pousser sans demande.
