-- Statistiques détaillées par match (absentes de worldcup-full.json, à importer à part)
-- et vues du tournoi des onglets Joueurs et Équipes. Copie : donneesStats.sql, parties 4 et 5.
-- NULL = donnée inconnue, 0 = valeur mesurée nulle.

CREATE TABLE player_match_stats (
    match_id              INT NOT NULL REFERENCES match(id) ON DELETE CASCADE,
    player_id             INT NOT NULL REFERENCES player(id),
    team_id               INT NOT NULL REFERENCES team(id),
    fouls_committed       SMALLINT CHECK (fouls_committed >= 0),
    free_kicks_won        SMALLINT CHECK (free_kicks_won >= 0),
    free_kicks_conceded   SMALLINT CHECK (free_kicks_conceded >= 0),
    duels_won             SMALLINT CHECK (duels_won >= 0),
    duels_lost            SMALLINT CHECK (duels_lost >= 0),
    corners               SMALLINT CHECK (corners >= 0),            -- corners tirés
    offsides              SMALLINT CHECK (offsides >= 0),
    distance_km           NUMERIC(5, 2) CHECK (distance_km >= 0),
    is_man_of_the_match   BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (match_id, player_id)
);

-- Un seul homme du match par rencontre
CREATE UNIQUE INDEX uq_player_stats_motm
    ON player_match_stats(match_id) WHERE is_man_of_the_match;
CREATE INDEX idx_player_stats_player ON player_match_stats(player_id);

-- Totaux d'équipe tels que publiés. Facultatifs : à défaut, v_team_tournament_stats additionne
-- les lignes joueurs du match (les corners et hors-jeu sont souvent publiés
-- seulement au niveau de l'équipe).
CREATE TABLE team_match_stats (
    match_id              INT NOT NULL REFERENCES match(id) ON DELETE CASCADE,
    team_id               INT NOT NULL REFERENCES team(id),
    fouls_committed       SMALLINT CHECK (fouls_committed >= 0),
    free_kicks_won        SMALLINT CHECK (free_kicks_won >= 0),
    free_kicks_conceded   SMALLINT CHECK (free_kicks_conceded >= 0),
    duels_won             SMALLINT CHECK (duels_won >= 0),
    duels_lost            SMALLINT CHECK (duels_lost >= 0),
    corners               SMALLINT CHECK (corners >= 0),
    offsides              SMALLINT CHECK (offsides >= 0),
    distance_km           NUMERIC(6, 2) CHECK (distance_km >= 0),
    PRIMARY KEY (match_id, team_id)
);

-- Vues du tournoi : une ligne par joueur (ou équipe) et par compétition.
-- Matchs joués, minutes, buts et notes viennent des tables de notes : relancer
-- le skill notation-matchs après chaque import pour que les vues soient à jour.
--
-- Note globale : moyenne des notes de match (rating). Pour les joueurs, s'y ajoute
-- une moyenne pondérée par les minutes jouées (rating_weighted), pour qu'une
-- entrée de 5 minutes ne pèse pas autant qu'un match complet.
-- Cartons : yellow_cards = jaunes ('Y'), red_cards = rouges directs et deuxièmes
-- jaunes ('R' et 'Y/R'). Le premier jaune d'une double sanction reste un jaune.
-- detailed_stats_matches = nombre de matchs couverts par player_match_stats / team_match_stats.

-- Onglet "Joueurs" -------------------------------------------------------------
-- Seuls les joueurs entrés en jeu sont listés ; un carton reçu sur le banc n'est
-- pas compté, comme dans la notation.
CREATE VIEW v_player_tournament_stats AS
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
       ROUND(SUM(r.rating * r.minutes_played) / NULLIF(SUM(r.minutes_played), 0), 1) AS rating_weighted
FROM player_match_rating r
JOIN match m        ON m.id = r.match_id
JOIN player pl      ON pl.id = r.player_id
JOIN team t         ON t.id = r.team_id
JOIN match_lineup l ON l.match_id = r.match_id AND l.player_id = r.player_id
LEFT JOIN player_match_stats s ON s.match_id = r.match_id AND s.player_id = r.player_id
CROSS JOIN LATERAL (
    SELECT COUNT(*) FILTER (WHERE b.card = 'Y')           AS yellow,
           COUNT(*) FILTER (WHERE b.card IN ('R', 'Y/R')) AS red
    FROM booking b WHERE b.match_id = r.match_id AND b.player_id = r.player_id
) bk
GROUP BY m.competition_id, r.player_id, pl.name, r.team_id, t.name;

-- Onglet "Équipes" -------------------------------------------------------------
-- Statistiques détaillées : total publié de l'équipe (team_match_stats) s'il
-- existe, sinon somme des joueurs du match (player_match_stats).
CREATE VIEW v_team_tournament_stats AS
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
       ROUND(AVG(r.rating), 1)                            AS rating
FROM team_match_rating r
JOIN match m       ON m.id = r.match_id
JOIN team t        ON t.id = r.team_id
JOIN team_match tm ON tm.match_id = r.match_id AND tm.team_id = r.team_id
CROSS JOIN LATERAL (
    SELECT COUNT(*) FILTER (WHERE b.card = 'Y')           AS yellow,
           COUNT(*) FILTER (WHERE b.card IN ('R', 'Y/R')) AS red
    FROM booking b WHERE b.match_id = r.match_id AND b.team_id = r.team_id
) bk
GROUP BY m.competition_id, r.team_id, t.name;
