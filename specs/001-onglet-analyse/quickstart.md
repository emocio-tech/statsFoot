# Quickstart – valider l'onglet Analyse

Guide de validation de bout en bout. Contrat : [contracts/api.md](contracts/api.md) ; modèle :
[data-model.md](data-model.md).

## Prérequis

- Branche `initDonneesNotation`, PostgreSQL 17 démarré, bases `analysefoot` (données importées)
  et `analysefoot_test` (tests).
- Hook de tests actif : `git config core.hooksPath .githooks`.

## 1. Tests automatisés

```bash
./mvnw -pl backend test
```

Attendu : tous les tests passent, dont ceux qui :

- exécutent les parties de données de `donneesStats.sql` (3 et 6) sur la base de test migrée par
  Flyway — jamais les parties de schéma 1, 2, 4 et 5 (constitution I) ;
- vérifient que l'en-tête de `donneesStats.sql` cite chaque migration V1 à V4 ;
- vérifient les règles des passes décisives (aucune sur penalty ni contre son camp, passeur
  coéquipier présent sur le terrain) et l'unicité du gardien par équipe et par match ;
- vérifient, pour chaque critère du catalogue, le vainqueur **et** la règle de départage ;
- vérifient la présence de `fictitiousFields` dans `/api/stats/*` ;
- vérifient la reconnaissance par mots-clés (reconnue, ambiguë, non reconnue).

## 2. Mettre la base de développement à jour

1. Démarrer l'application une fois : Flyway applique la V4.
2. Régénérer notes et stats fictives (dont les passes décisives) en n'exécutant que les parties
   de données (3 et 6), jamais le fichier entier sur une base gérée par Flyway :

   ```bash
   F=backend/src/main/resources/db/migration/donneesStats.sql
   { echo '\set ON_ERROR_STOP on'
     sed -n '/^-- PARTIE 3 /,/^-- PARTIE 4 /p' "$F" | sed '$d'
     sed -n '/^-- PARTIE 6 /,$p' "$F"; } \
     | psql -U analysefoot -h localhost -d analysefoot -v ON_ERROR_STOP=1
   ```

3. Contrôles de cohérence des notes (constitution II, skill `notation-matchs`) : 3 288 notes
   joueurs (titulaires + remplaçants entrés en jeu), 208 notes équipes (104 matchs × 2),
   moyennes proches de 6.
4. Contrôles des données V4 : 208 lignes `match_lineup.is_goalkeeper` vraies (104 matchs × 2
   équipes) ; aucune passe décisive sur un penalty ou un but contre son camp ; chaque passeur est
   un coéquipier présent sur le terrain.

## 3. Parcours manuel (http://localhost:8080/analyse)

| Action | Résultat attendu |
|--------|------------------|
| Clic « Meilleur buteur » | Kylian Mbappé, 10 buts ; top 5 avec Haaland (476 min) devant Bellingham (616 min) |
| Clic « Meilleur passeur » | Michael Olise (France), 6 passes décisives, mention « données fictives » (tirage `fictif-v1`, validé le 21/10/2026) |
| Clic « Joueur le plus agressif » | Issa Diop (Maroc), 3 jaunes |
| Clic « Joueur le moins fair-play » | Jarell Quansah (Angleterre), -5 points, 200 min (départage par les minutes) |
| Clic « Meilleure équipe » | Espagne, championne ; puis Argentine (finaliste), Angleterre (3e), France (4e), quart de finaliste le mieux noté |
| Toute question sauf « Meilleur passeur » | aucune mention « données fictives » sur le critère |
| Onglets Joueurs et Équipes | colonnes fictives marquées d'un astérisque, légende « * données fictives » |
| Clic « Équipe qui a marqué le plus de buts » | France (20 buts, +10), Angleterre juste derrière |
| Clic « Équipe la moins fair-play » | Argentine, -16 |
| Clic « Joueur le plus fair-play » | Lionel Messi, 740 minutes sans carton (gardiens exclus) |
| Saisie « qui a pris le plus de jaunes » | même réponse que « Joueur le plus agressif » |
| Saisie « le moins fair-play » | choix proposé entre joueur et équipe |
| Saisie « quel temps fera-t-il » | question non reconnue, liste des 8 questions |
| Sous une réponse | rapport complet, stats fictives signalées |
| Onglet Joueurs, clic sur une ligne | `/joueurs/:id`, même rapport |
| Onglet Équipes, clic sur une ligne | `/equipes/:id`, même rapport |

Chaque réponse doit s'afficher en moins de 2 secondes (SC-002). Vu le volume (research R9),
cette vérification manuelle suffit ; aucun test de charge n'est prévu.
