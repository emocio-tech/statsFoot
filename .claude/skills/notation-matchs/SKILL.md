---
name: notation-matchs
description: Calcule et met à jour en base PostgreSQL les notes de 0 à 10 de chaque joueur et de chaque équipe pour chaque match (tables player_match_rating et team_match_rating). À utiliser pour (re)noter les matchs après un import de données, modifier le barème de notation, ou expliquer une note.
---

# Notation des matchs

Note chaque joueur entré en jeu et chaque équipe, pour chaque match présent en base, sur une échelle de 0 à 10.
Le calcul est entièrement fait en SQL par `compute_ratings.sql` (dans le dossier de ce skill) : il efface et recalcule toutes les notes dans une seule transaction, on peut donc le relancer autant de fois que nécessaire.

## Prérequis

- PostgreSQL démarré. Installation locale de ce poste :
  ```powershell
  & "$env:LOCALAPPDATA\Programs\PostgreSQL\17\bin\pg_ctl.exe" -D "$env:LOCALAPPDATA\PostgreSQL\17\data" -l "$env:LOCALAPPDATA\PostgreSQL\17\postgres.log" start
  ```
- Les tables de notes existent : elles sont créées par la migration Flyway `V2__match_ratings.sql`, appliquée au démarrage du backend. Ne jamais les créer à la main.
- Des matchs importés (voir l'import `worldcup-full.json` dans le README).

Connexion par défaut : base `analysefoot`, utilisateur `analysefoot`, mot de passe `analysefoot`, sur `localhost:5432` (surchargeables par `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` côté application).

## Procédure

1. **Vérifier la base.** Les deux requêtes doivent renvoyer `2` et un nombre de matchs > 0 :
   ```powershell
   $env:PGPASSWORD = "analysefoot"
   $psql = "$env:LOCALAPPDATA\Programs\PostgreSQL\17\bin\psql.exe"
   & $psql -U analysefoot -h localhost -d analysefoot -Atc "select max(version::int) from flyway_schema_history where success" -c "select count(*) from match"
   ```
   Si la version est 1, démarrer le backend une fois pour que Flyway applique V2.

2. **Lancer le calcul :**
   ```powershell
   & $psql -U analysefoot -h localhost -d analysefoot -v ON_ERROR_STOP=1 -f ".claude/skills/notation-matchs/compute_ratings.sql"
   ```

3. **Contrôler le résultat** avant d'annoncer quoi que ce soit :
   ```sql
   -- un joueur noté par apparition, une note par équipe et par match (2 x nombre de matchs)
   select (select count(*) from player_match_rating) joueurs,
          (select count(*) from team_match_rating)   equipes,
          (select count(*) from match) * 2            equipes_attendues;
   -- distribution : la moyenne doit rester proche de 6
   select round(avg(rating), 2) moyenne, min(rating), max(rating) from player_match_rating;
   select round(avg(rating), 2) moyenne, min(rating), max(rating) from team_match_rating;
   ```
   Le nombre de notes joueurs doit égaler le nombre de titulaires plus le nombre de remplaçants entrés en jeu.

4. **Faire un compte rendu** : nombre de notes écrites, version du barème, meilleures et moins bonnes notes avec leur explication (les colonnes de détail le permettent), et toute anomalie.

## Barème (version v1)

Note = `6.0 + coef_tour × (contribution)`, arrondie à 0,1 puis bornée à [0, 10].
Un match neutre vaut donc 6. Toutes les valeurs sont dans la table temporaire `p` au début du script.

### Coefficient du tour

Plus le tour est décisif, plus les performances (bonnes ou mauvaises) pèsent :

| Phase | Groupes | 32es | 16es | Quarts | Demies | 3e place | Finale |
|---|---|---|---|---|---|---|---|
| Coef | 1,00 | 1,05 | 1,10 | 1,15 | 1,20 | 1,05 | 1,30 |

### Joueur

**Contribution collective**, pondérée par le temps de jeu (poids = minutes jouées / 60, plafonné à 1) :

| Critère | Points |
|---|---|
| Victoire / défaite (temps réglementaire + prolongation) | +0,5 / −0,5 |
| Tirs au but gagnés | +0,2 |
| Buts de l'équipe pendant sa présence | +0,3 chacun |
| Buts encaissés pendant sa présence | −0,3 chacun |
| Plafond de ces deux lignes | ±1,0 |
| Clean sheet de l'équipe (si 60 min jouées au moins) | +0,3 |

**Actions individuelles**, comptées en entier quel que soit le temps de jeu :

| Critère | Points |
|---|---|
| But dans le jeu / sur penalty | +1,0 / +0,7 |
| But contre son camp | −1,0 |
| But de la victoire (celui qui dépasse le total final adverse) | +0,5 |
| But égalisateur final d'un match nul | +0,3 |
| Carton jaune | −0,5 |
| Deuxième jaune (expulsion, en plus du premier jaune) | −1,0 |
| Rouge direct | −2,0 |
| Tir au but réussi / raté | +0,2 / −0,4 |

Le temps de jeu se calcule de l'entrée (coup d'envoi ou remplacement) à la sortie (remplacement ou expulsion), sur 90 minutes, ou 120 en cas de prolongation.
Seuls les joueurs entrés en jeu sont notés : un remplaçant resté sur le banc n'a pas de note.

### Équipe

| Critère | Points |
|---|---|
| Victoire / défaite | +1,5 / −1,5 |
| Tirs au but gagnés / perdus | +0,3 / −0,3 |
| Différence de buts | ±0,4 par but, plafonné à ±2,0 |
| Buts marqués | +0,2 par but, plafonné à +1,0 |
| Clean sheet | +0,5 |
| Fair-play | −0,1 par point FIFA perdu |

Les points FIFA de fair-play sont : jaune −1, deuxième jaune −3 au total, rouge direct −4.
La colonne `points` contient les points du résultat à l'issue du temps de jeu (3 / 1 / 0) : une victoire aux tirs au but compte comme un nul.

## Modifier le barème

1. Changer les valeurs dans la table `p` ou dans `stage_coef` de `compute_ratings.sql`.
2. Incrémenter `version` (`v2`, `v3`...) : chaque note enregistre la version qui l'a produite.
3. Relancer la procédure, puis comparer les distributions avec la version précédente si l'utilisateur le demande.

Ajouter un critère qui demande une nouvelle colonne passe par une nouvelle migration Flyway (`V3__...sql`) dans `backend/src/main/resources/db/migration`, jamais par un `ALTER TABLE` lancé à la main.

## Limites des données

Les données sources ne donnent ni les postes, ni les passes décisives, ni les tirs, ni la possession.
La performance d'un joueur s'évalue donc à partir des buts, des cartons, du résultat et du différentiel de buts pendant sa présence.
Un gardien ou un défenseur est surtout valorisé par le clean sheet et le faible nombre de buts encaissés.
