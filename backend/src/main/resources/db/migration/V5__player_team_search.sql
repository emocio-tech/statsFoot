-- Recherche et comparaison de joueurs et d'équipes (code issu de la branche main).
--   team_fifa_code      : code FIFA de chaque équipe (table de référence, lue par TeamEntity)
--   goalkeeper_matches  : matchs joués comme gardien, ajoutés à la fin de v_player_tournament_stats
--   captain_matches     : matchs joués comme capitaine, idem
-- Colonnes existantes de la vue inchangées ; colonnes nouvelles ajoutées à la fin.

CREATE TABLE team_fifa_code (
    team_name  VARCHAR(100) PRIMARY KEY,
    code       CHAR(3)      NOT NULL UNIQUE
);

INSERT INTO team_fifa_code (team_name, code) VALUES
    ('Algeria', 'ALG'),
    ('Argentina', 'ARG'),
    ('Australia', 'AUS'),
    ('Austria', 'AUT'),
    ('Belgium', 'BEL'),
    ('Bosnia and Herzegovina', 'BIH'),
    ('Brazil', 'BRA'),
    ('Cabo Verde', 'CPV'),
    ('Canada', 'CAN'),
    ('Colombia', 'COL'),
    ('Congo DR', 'COD'),
    ('Croatia', 'CRO'),
    ('Curaçao', 'CUW'),
    ('Czech Republic', 'CZE'),
    ('Côte d''Ivoire', 'CIV'),
    ('Ecuador', 'ECU'),
    ('Egypt', 'EGY'),
    ('England', 'ENG'),
    ('France', 'FRA'),
    ('Germany', 'GER'),
    ('Ghana', 'GHA'),
    ('Haiti', 'HAI'),
    ('Iran', 'IRN'),
    ('Iraq', 'IRQ'),
    ('Japan', 'JPN'),
    ('Jordan', 'JOR'),
    ('Mexico', 'MEX'),
    ('Morocco', 'MAR'),
    ('Netherlands', 'NED'),
    ('New Zealand', 'NZL'),
    ('Norway', 'NOR'),
    ('Panama', 'PAN'),
    ('Paraguay', 'PAR'),
    ('Portugal', 'POR'),
    ('Qatar', 'QAT'),
    ('Saudi Arabia', 'KSA'),
    ('Scotland', 'SCO'),
    ('Senegal', 'SEN'),
    ('South Africa', 'RSA'),
    ('South Korea', 'KOR'),
    ('Spain', 'ESP'),
    ('Sweden', 'SWE'),
    ('Switzerland', 'SUI'),
    ('Tunisia', 'TUN'),
    ('Turkey', 'TUR'),
    ('USA', 'USA'),
    ('Uruguay', 'URU'),
    ('Uzbekistan', 'UZB');

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
       BOOL_OR(l.is_goalkeeper)                           AS is_goalkeeper,
       COUNT(*) FILTER (WHERE l.is_goalkeeper)            AS goalkeeper_matches,
       COUNT(*) FILTER (WHERE l.is_captain)               AS captain_matches
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
