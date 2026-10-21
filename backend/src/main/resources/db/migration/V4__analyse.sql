-- Onglet Analyse : gardien de chaque feuille de match (donnée réelle), passes décisives (FICTIVES,
-- remplies par la partie 6 de donneesStats.sql) et colonnes ajoutées aux vues du tournoi.
-- Copie : donneesStats.sql, parties 4 et 5.

-- Gardien --------------------------------------------------------------------------
-- L'import marque le premier titulaire de chaque composition (le gardien dans worldcup-full.json).
-- Rétro-remplissage des matchs déjà importés : le n°1 de l'équipe (son premier joueur créé) s'il
-- est titulaire, sinon le titulaire jamais aligné en même temps que lui. Règle vérifiée sur
-- worldcup-full.json le 21/10/2026, rotations de gardiens comprises.
ALTER TABLE match_lineup ADD COLUMN is_goalkeeper BOOLEAN NOT NULL DEFAULT FALSE;

WITH first_gk AS (
    SELECT team_id, MIN(id) AS player_id FROM player GROUP BY team_id
), starter AS (
    SELECT match_id, team_id, player_id FROM match_lineup WHERE is_starter
), goalkeeper AS (
    SELECT s.match_id, s.player_id
    FROM starter s JOIN first_gk g ON g.player_id = s.player_id
    UNION ALL
    SELECT * FROM (
        SELECT DISTINCT ON (s.match_id, s.team_id) s.match_id, s.player_id
        FROM starter s
        JOIN first_gk g ON g.team_id = s.team_id
        WHERE NOT EXISTS (SELECT 1 FROM starter x WHERE x.match_id = s.match_id AND x.player_id = g.player_id)
          AND NOT EXISTS (SELECT 1 FROM starter a JOIN starter b ON b.match_id = a.match_id AND b.player_id = g.player_id
                          WHERE a.player_id = s.player_id)
        ORDER BY s.match_id, s.team_id, s.player_id
    ) backup
)
UPDATE match_lineup l SET is_goalkeeper = TRUE
FROM goalkeeper g
WHERE l.match_id = g.match_id AND l.player_id = g.player_id;

-- Un seul gardien titulaire par équipe et par match
CREATE UNIQUE INDEX uq_lineup_goalkeeper ON match_lineup(match_id, team_id) WHERE is_goalkeeper;

-- Passes décisives (FICTIVES) ----------------------------------------------------------
-- Au plus une par but ; jamais sur un penalty ni un but contre son camp ; passeur coéquipier du
-- buteur, sur le terrain à l'instant du but (règles appliquées par la partie 6 de donneesStats.sql).
CREATE TABLE goal_assist (
    goal_id    INT PRIMARY KEY REFERENCES goal(id) ON DELETE CASCADE,
    player_id  INT NOT NULL REFERENCES player(id)
);

CREATE INDEX idx_goal_assist_player ON goal_assist(player_id);

-- Vues du tournoi --------------------------------------------------------------------
-- Colonnes existantes inchangées ; colonnes nouvelles ajoutées à la fin.
--   assists          : NULL tant qu'aucune passe n'a été générée (donnée inconnue)
--   fair_play_points : barème FIFA, jaune -1, deuxième jaune -2 (-3 avec le premier), rouge -4
--   is_goalkeeper    : gardien d'au moins un match
CREATE OR REPLACE VIEW v_player_tournament_stats AS
SELECT m.competition_id,
       r.player_id,
       pl.name                                            AS player,
       r.team_id,
       t.name                                             AS team,
       COUNT(*)                                           AS matches_played,
       COUNT(*) FILTER (WHERE l.is_starter)               AS matches_started,
       SUM(r.minutes_played)                              AS minutes_played,
       SUM(r.goals)                                       AS goals,
       SUM(r.penalty_goals)                               AS penalty_goals,
       SUM(r.own_goals)                                   AS own_goals,
       SUM(bk.yellow)                                     AS yellow_cards,
       SUM(bk.red)                                        AS red_cards,
       SUM(s.fouls_committed)                             AS fouls_committed,
       SUM(s.free_kicks_won)                              AS free_kicks_won,
       SUM(s.free_kicks_conceded)                         AS free_kicks_conceded,
       SUM(s.duels_won)                                   AS duels_won,
       SUM(s.duels_lost)                                  AS duels_lost,
       SUM(s.corners)                                     AS corners,
       SUM(s.offsides)                                    AS offsides,
       SUM(s.distance_km)                                 AS distance_km,
       CASE WHEN COUNT(s.match_id) > 0
            THEN COUNT(*) FILTER (WHERE s.is_man_of_the_match) END AS man_of_the_match_count,
       CASE WHEN COUNT(s.match_id) > 0
            THEN BOOL_OR(s.is_man_of_the_match) END       AS man_of_the_match,
       COUNT(s.match_id)                                  AS detailed_stats_matches,
       ROUND(AVG(r.rating), 1)                            AS rating,
       ROUND(SUM(r.rating * r.minutes_played) / NULLIF(SUM(r.minutes_played), 0), 1) AS rating_weighted,
       CASE WHEN EXISTS (SELECT 1 FROM goal_assist) THEN SUM(ga.assists) END AS assists,
       SUM(bk.second_yellow)                              AS second_yellows,
       SUM(bk.direct_red)                                 AS direct_reds,
       SUM(bk.fair_play)                                  AS fair_play_points,
       SUM(r.decisive_goals)                              AS decisive_goals,
       BOOL_OR(l.is_goalkeeper)                           AS is_goalkeeper
FROM player_match_rating r
JOIN match m        ON m.id = r.match_id
JOIN player pl      ON pl.id = r.player_id
JOIN team t         ON t.id = r.team_id
JOIN match_lineup l ON l.match_id = r.match_id AND l.player_id = r.player_id
LEFT JOIN player_match_stats s ON s.match_id = r.match_id AND s.player_id = r.player_id
CROSS JOIN LATERAL (
    SELECT COUNT(*) FILTER (WHERE b.card = 'Y')           AS yellow,
           COUNT(*) FILTER (WHERE b.card IN ('R', 'Y/R')) AS red,
           COUNT(*) FILTER (WHERE b.card = 'Y/R')         AS second_yellow,
           COUNT(*) FILTER (WHERE b.card = 'R')           AS direct_red,
           COALESCE(SUM(CASE b.card WHEN 'Y' THEN -1 WHEN 'Y/R' THEN -2 WHEN 'R' THEN -4 END), 0) AS fair_play
    FROM booking b WHERE b.match_id = r.match_id AND b.player_id = r.player_id
) bk
CROSS JOIN LATERAL (
    SELECT COUNT(*) AS assists
    FROM goal_assist a JOIN goal g ON g.id = a.goal_id
    WHERE g.match_id = r.match_id AND a.player_id = r.player_id
) ga
GROUP BY m.competition_id, r.player_id, pl.name, r.team_id, t.name;

-- stage_reached  : phase du dernier match joué
-- final_position : 1 champion (vainqueur de la finale, tirs au but compris), 2 finaliste,
--                  3 vainqueur du match pour la 3e place, 4 perdant de ce match (ou demi-finaliste
--                  sans match pour la 3e place), 5 quart, 6 huitième, 7 seizième, 8 phase de groupes
CREATE OR REPLACE VIEW v_team_tournament_stats AS
WITH team_match AS (
    SELECT r.match_id, r.team_id,
           COALESCE(ts.fouls_committed,     ps.fouls_committed)     AS fouls_committed,
           COALESCE(ts.free_kicks_won,      ps.free_kicks_won)      AS free_kicks_won,
           COALESCE(ts.free_kicks_conceded, ps.free_kicks_conceded) AS free_kicks_conceded,
           COALESCE(ts.duels_won,           ps.duels_won)           AS duels_won,
           COALESCE(ts.duels_lost,          ps.duels_lost)          AS duels_lost,
           COALESCE(ts.corners,             ps.corners)             AS corners,
           COALESCE(ts.offsides,            ps.offsides)            AS offsides,
           COALESCE(ts.distance_km,         ps.distance_km)         AS distance_km,
           (ts.match_id IS NOT NULL OR ps.lines > 0)                AS has_detailed_stats
    FROM team_match_rating r
    LEFT JOIN team_match_stats ts ON ts.match_id = r.match_id AND ts.team_id = r.team_id
    CROSS JOIN LATERAL (
        SELECT COUNT(*) AS lines,
               SUM(s.fouls_committed) AS fouls_committed, SUM(s.free_kicks_won) AS free_kicks_won,
               SUM(s.free_kicks_conceded) AS free_kicks_conceded,
               SUM(s.duels_won) AS duels_won, SUM(s.duels_lost) AS duels_lost,
               SUM(s.corners) AS corners, SUM(s.offsides) AS offsides, SUM(s.distance_km) AS distance_km
        FROM player_match_stats s WHERE s.match_id = r.match_id AND s.team_id = r.team_id
    ) ps
), last_match AS (
    SELECT DISTINCT ON (m.competition_id, r.team_id) m.competition_id, r.team_id, m.stage,
           (r.result = 'W' OR (r.result = 'D' AND COALESCE(r.won_shootout, FALSE))) AS won
    FROM team_match_rating r JOIN match m ON m.id = r.match_id
    ORDER BY m.competition_id, r.team_id, m.kickoff_at DESC, m.id DESC
)
SELECT m.competition_id,
       r.team_id,
       t.name                                             AS team,
       COUNT(*)                                           AS matches_played,
       COUNT(*) FILTER (WHERE r.result = 'W')             AS wins,
       COUNT(*) FILTER (WHERE r.result = 'D')             AS draws,
       COUNT(*) FILTER (WHERE r.result = 'L')             AS losses,
       SUM(r.points)                                      AS points,
       SUM(r.goals_for)                                   AS goals_for,
       SUM(r.goals_against)                               AS goals_against,
       SUM(bk.yellow)                                     AS yellow_cards,
       SUM(bk.red)                                        AS red_cards,
       SUM(r.fair_play_points)                            AS fair_play_points,
       SUM(tm.fouls_committed)                            AS fouls_committed,
       SUM(tm.free_kicks_won)                             AS free_kicks_won,
       SUM(tm.free_kicks_conceded)                        AS free_kicks_conceded,
       SUM(tm.duels_won)                                  AS duels_won,
       SUM(tm.duels_lost)                                 AS duels_lost,
       SUM(tm.corners)                                    AS corners,
       SUM(tm.offsides)                                   AS offsides,
       SUM(tm.distance_km)                                AS distance_km,
       COUNT(*) FILTER (WHERE tm.has_detailed_stats)      AS detailed_stats_matches,
       ROUND(AVG(r.rating), 1)                            AS rating,
       MIN(lm.stage)                                      AS stage_reached,
       MIN(CASE lm.stage
               WHEN 'FINAL'         THEN CASE WHEN lm.won THEN 1 ELSE 2 END
               WHEN 'THIRD_PLACE'   THEN CASE WHEN lm.won THEN 3 ELSE 4 END
               WHEN 'SEMI_FINAL'    THEN 4
               WHEN 'QUARTER_FINAL' THEN 5
               WHEN 'ROUND_OF_16'   THEN 6
               WHEN 'ROUND_OF_32'   THEN 7
               ELSE 8
           END)                                           AS final_position
FROM team_match_rating r
JOIN match m       ON m.id = r.match_id
JOIN team t        ON t.id = r.team_id
JOIN team_match tm ON tm.match_id = r.match_id AND tm.team_id = r.team_id
JOIN last_match lm ON lm.competition_id = m.competition_id AND lm.team_id = r.team_id
CROSS JOIN LATERAL (
    SELECT COUNT(*) FILTER (WHERE b.card = 'Y')           AS yellow,
           COUNT(*) FILTER (WHERE b.card IN ('R', 'Y/R')) AS red
    FROM booking b WHERE b.match_id = r.match_id AND b.team_id = r.team_id
) bk
GROUP BY m.competition_id, r.team_id, t.name;
