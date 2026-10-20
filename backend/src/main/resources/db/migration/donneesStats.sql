-- =============================================================================
-- donneesStats.sql : synthèse du modèle de données et de la notation statsFoot
-- =============================================================================
-- Regroupe en un seul script :
--   Partie 1  schéma des compétitions  (V1__init_schema.sql)
--   Partie 2  tables de notes          (V2__match_ratings.sql)
--   Partie 3  calcul des notes 0 à 10  (compute_ratings.sql du skill notation-matchs)
--   Partie 4  statistiques détaillées par match (V3__tournament_stats.sql)
--   Partie 5  vues des onglets "Joueurs" et "Équipes" (V3__tournament_stats.sql)
--   Partie 6  stats détaillées FICTIVES (efface et régénère les tables de la partie 4)
--
-- Usage (psql) :
--   psql -U analysefoot -h localhost -d <base> -v ON_ERROR_STOP=1 -f donneesStats.sql
--
-- Les parties 1, 2 et 4 créent les tables absentes. Elles ne modifient pas une table
-- existante. La partie 3 efface et recalcule toutes les notes à chaque exécution.
-- La partie 5 (re)crée les vues ; elle ne stocke rien.
--
-- Dans l'application, le schéma reste géré par Flyway (backend/src/main/resources/db/migration).
-- Ce fichier en est une copie de lecture et sert aussi de script autonome. Il ne doit
-- pas être lancé sur une base que Flyway gère encore vide : Flyway refuserait ensuite
-- de migrer une base qui contient déjà des tables.
-- En cas de modification d'une migration ou du barème, mettre ce fichier à jour.
-- =============================================================================

\set ON_ERROR_STOP on


-- =============================================================================
-- PARTIE 1 : SCHÉMA DES COMPÉTITIONS (source : worldcup-full.json, format openfootball)
-- =============================================================================
--
--   competition ─< match >─ team (team1, team2)
--                    │  ├── stadium, referee
--                    │  ├─< match_lineup >── player >── team
--                    │  ├─< substitution   (player_in, player_out)
--                    │  ├─< goal           (team = bénéficiaire, player = buteur)
--                    │  ├─< booking        (Y, R, Y/R)
--                    │  └─< penalty_kick   (séance de tirs au but)
--
-- Conventions :
--   - minute + added_time : "90+5" est stocké comme minute 90, added_time 5.
--   - Scores par période : ht (mi-temps), ft (90 min), et (après prolongation,
--     NULL sinon), pen (tirs au but, NULL sinon).
--   - But contre son camp : team_id = équipe qui en bénéficie, player_id = auteur adverse.
--   - Deuxième jaune : une ligne 'Y' puis une ligne 'Y/R' pour le même joueur.

-- Référentiels ---------------------------------------------------------------

CREATE TABLE IF NOT EXISTS competition (
    id    SERIAL PRIMARY KEY,
    name  VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS team (
    id    SERIAL PRIMARY KEY,
    name  VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS stadium (
    id    SERIAL PRIMARY KEY,
    name  VARCHAR(150) NOT NULL,
    city  VARCHAR(100) NOT NULL,
    UNIQUE (name, city)
);

CREATE TABLE IF NOT EXISTS referee (
    id            SERIAL PRIMARY KEY,
    name          VARCHAR(150) NOT NULL UNIQUE,
    country_code  CHAR(3)      NOT NULL
);

CREATE TABLE IF NOT EXISTS player (
    id       SERIAL PRIMARY KEY,
    name     VARCHAR(150) NOT NULL,
    team_id  INT NOT NULL REFERENCES team(id),
    UNIQUE (name, team_id)
);

-- Match ----------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS match (
    id              SERIAL PRIMARY KEY,
    competition_id  INT NOT NULL REFERENCES competition(id),
    stage           VARCHAR(20) NOT NULL CHECK (stage IN
                      ('GROUP', 'ROUND_OF_32', 'ROUND_OF_16', 'QUARTER_FINAL',
                       'SEMI_FINAL', 'THIRD_PLACE', 'FINAL')),
    group_name      CHAR(1),                  -- 'A'..'L', NULL hors phase de groupes
    kickoff_at      TIMESTAMPTZ NOT NULL,
    stadium_id      INT NOT NULL REFERENCES stadium(id),
    attendance      INT,
    referee_id      INT REFERENCES referee(id),
    team1_id        INT NOT NULL REFERENCES team(id),
    team2_id        INT NOT NULL REFERENCES team(id),
    ht_team1        SMALLINT,
    ht_team2        SMALLINT,
    ft_team1        SMALLINT,
    ft_team2        SMALLINT,
    et_team1        SMALLINT,                 -- prolongation, NULL sinon
    et_team2        SMALLINT,
    pen_team1       SMALLINT,                 -- tirs au but, NULL sinon
    pen_team2       SMALLINT,
    CHECK (team1_id <> team2_id)
);

-- Feuille de match -----------------------------------------------------------

CREATE TABLE IF NOT EXISTS match_lineup (
    match_id    INT NOT NULL REFERENCES match(id) ON DELETE CASCADE,
    player_id   INT NOT NULL REFERENCES player(id),
    team_id     INT NOT NULL REFERENCES team(id),
    is_starter  BOOLEAN NOT NULL,
    is_captain  BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (match_id, player_id)
);

CREATE TABLE IF NOT EXISTS substitution (
    id             SERIAL PRIMARY KEY,
    match_id       INT NOT NULL REFERENCES match(id) ON DELETE CASCADE,
    team_id        INT NOT NULL REFERENCES team(id),
    player_in_id   INT NOT NULL REFERENCES player(id),
    player_out_id  INT NOT NULL REFERENCES player(id),
    minute         SMALLINT NOT NULL,
    added_time     SMALLINT
);

CREATE TABLE IF NOT EXISTS goal (
    id           SERIAL PRIMARY KEY,
    match_id     INT NOT NULL REFERENCES match(id) ON DELETE CASCADE,
    team_id      INT NOT NULL REFERENCES team(id),    -- équipe qui bénéficie du but
    player_id    INT NOT NULL REFERENCES player(id),  -- buteur (adversaire si CSC)
    minute       SMALLINT NOT NULL,
    added_time   SMALLINT,
    is_penalty   BOOLEAN NOT NULL DEFAULT FALSE,
    is_own_goal  BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS booking (
    id          SERIAL PRIMARY KEY,
    match_id    INT NOT NULL REFERENCES match(id) ON DELETE CASCADE,
    team_id     INT NOT NULL REFERENCES team(id),
    player_id   INT NOT NULL REFERENCES player(id),
    card        VARCHAR(3) NOT NULL CHECK (card IN ('Y', 'R', 'Y/R')),
    minute      SMALLINT NOT NULL,
    added_time  SMALLINT
);

CREATE TABLE IF NOT EXISTS penalty_kick (
    match_id    INT NOT NULL REFERENCES match(id) ON DELETE CASCADE,
    kick_order  SMALLINT NOT NULL,
    team_id     INT NOT NULL REFERENCES team(id),
    player_id   INT NOT NULL REFERENCES player(id),
    scored      BOOLEAN NOT NULL,
    PRIMARY KEY (match_id, kick_order)
);

CREATE INDEX IF NOT EXISTS idx_match_stage    ON match(stage, group_name);
CREATE INDEX IF NOT EXISTS idx_goal_player    ON goal(player_id);
CREATE INDEX IF NOT EXISTS idx_booking_player ON booking(player_id);
CREATE INDEX IF NOT EXISTS idx_lineup_player  ON match_lineup(player_id);


-- =============================================================================
-- PARTIE 2 : TABLES DE NOTES (remplies par la partie 3)
-- =============================================================================

CREATE TABLE IF NOT EXISTS player_match_rating (
    match_id                INT NOT NULL REFERENCES match(id) ON DELETE CASCADE,
    player_id               INT NOT NULL REFERENCES player(id),
    team_id                 INT NOT NULL REFERENCES team(id),
    rating                  NUMERIC(3, 1) NOT NULL CHECK (rating BETWEEN 0 AND 10),
    minutes_played          SMALLINT NOT NULL,
    goals                   SMALLINT NOT NULL,
    penalty_goals           SMALLINT NOT NULL,
    own_goals               SMALLINT NOT NULL,
    decisive_goals          SMALLINT NOT NULL,  -- but de la victoire ou égalisation finale
    goals_for_on_pitch      SMALLINT NOT NULL,  -- buts de l'équipe pendant sa présence
    goals_against_on_pitch  SMALLINT NOT NULL,
    yellow_cards            SMALLINT NOT NULL,
    sent_off                BOOLEAN  NOT NULL,
    shootout_scored         SMALLINT NOT NULL,
    shootout_missed         SMALLINT NOT NULL,
    algorithm_version       VARCHAR(20) NOT NULL,
    computed_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (match_id, player_id)
);

CREATE TABLE IF NOT EXISTS team_match_rating (
    match_id           INT NOT NULL REFERENCES match(id) ON DELETE CASCADE,
    team_id            INT NOT NULL REFERENCES team(id),
    rating             NUMERIC(3, 1) NOT NULL CHECK (rating BETWEEN 0 AND 10),
    result             CHAR(1) NOT NULL CHECK (result IN ('W', 'D', 'L')),  -- à l'issue du temps de jeu
    points             SMALLINT NOT NULL,                                   -- 3 / 1 / 0
    goals_for          SMALLINT NOT NULL,
    goals_against      SMALLINT NOT NULL,
    won_shootout       BOOLEAN,                                             -- NULL sans tirs au but
    fair_play_points   SMALLINT NOT NULL,                                   -- barème FIFA, <= 0
    algorithm_version  VARCHAR(20) NOT NULL,
    computed_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (match_id, team_id)
);

CREATE INDEX IF NOT EXISTS idx_player_rating_player ON player_match_rating(player_id);
CREATE INDEX IF NOT EXISTS idx_team_rating_team     ON team_match_rating(team_id);


-- =============================================================================
-- PARTIE 3 : CALCUL DES NOTES (barème v1)
-- =============================================================================
-- Note = 6.0 + coef_tour x contribution, arrondie à 0,1 et bornée à [0, 10].
--   Joueur : coef x (poids_temps x collectif + individuel)
--            poids_temps = minutes jouées / 60, plafonné à 1
--   Équipe : coef x (résultat + tirs au but + différence de buts + buts
--            + clean sheet + fair-play)
-- Les instants sont codés t = minute x 100 + temps additionnel (90+3 -> 9003).
-- Tout le calcul tient dans une transaction : les tables temporaires disparaissent au COMMIT.

BEGIN;

-- Paramètres -----------------------------------------------------------------
-- Changer une valeur = changer le barème : incrémenter alors "version".
CREATE TEMP TABLE p ON COMMIT DROP AS SELECT
    'v1'::varchar(20) AS version,
    6.0  AS base,                     -- note d'un match neutre
    -- Joueur : actions individuelles (non pondérées par le temps de jeu)
    1.0  AS goal,
    0.7  AS penalty_goal,
    -1.0 AS own_goal,
    0.5  AS winning_goal,             -- but qui donne la victoire
    0.3  AS equalizer_goal,           -- but égalisateur d'un match nul
    -0.5 AS yellow,
    -1.0 AS second_yellow,            -- en plus du premier jaune déjà compté
    -2.0 AS red,
    0.2  AS shootout_scored,
    -0.4 AS shootout_missed,
    -- Joueur : contribution collective (pondérée par le temps de jeu)
    0.5  AS player_win,
    -0.5 AS player_loss,
    0.2  AS player_shootout_win,
    0.3  AS goal_for_on_pitch,
    -0.3 AS goal_against_on_pitch,
    1.0  AS on_pitch_cap,             -- plafond du +/- en valeur absolue
    0.3  AS clean_sheet,
    60   AS clean_sheet_min_minutes,
    60   AS full_weight_minutes,      -- temps de jeu pour un poids collectif de 100 %
    -- Équipe
    1.5  AS team_win,
    -1.5 AS team_loss,
    0.3  AS team_shootout,            -- + au vainqueur, - au perdant
    0.4  AS team_goal_diff,
    2.0  AS team_goal_diff_cap,
    0.2  AS team_goal,
    1.0  AS team_goal_cap,
    0.5  AS team_clean_sheet,
    0.1  AS fair_play_point;          -- par point FIFA perdu

-- Les écarts à la note de base pèsent plus lourd dans les tours décisifs
CREATE TEMP TABLE stage_coef (stage VARCHAR(20) PRIMARY KEY, coef NUMERIC) ON COMMIT DROP;
INSERT INTO stage_coef VALUES
    ('GROUP', 1.00), ('ROUND_OF_32', 1.05), ('ROUND_OF_16', 1.10), ('QUARTER_FINAL', 1.15),
    ('SEMI_FINAL', 1.20), ('THIRD_PLACE', 1.05), ('FINAL', 1.30);

-- Étape 1 : résultat de chaque équipe (2 lignes par match) ---------------------
CREATE TEMP TABLE team_result ON COMMIT DROP AS
SELECT m.id AS match_id, s.team_id, s.opponent_id, m.stage, s.gf, s.ga,
       CASE WHEN s.gf > s.ga THEN 'W' WHEN s.gf < s.ga THEN 'L' ELSE 'D' END AS result,
       CASE WHEN s.pen_for IS NULL THEN NULL ELSE s.pen_for > s.pen_against END AS won_shootout,
       CASE WHEN m.et_team1 IS NULL THEN 90 ELSE 120 END AS end_minute
FROM match m
CROSS JOIN LATERAL (VALUES
    (m.team1_id, m.team2_id, COALESCE(m.et_team1, m.ft_team1), COALESCE(m.et_team2, m.ft_team2), m.pen_team1, m.pen_team2),
    (m.team2_id, m.team1_id, COALESCE(m.et_team2, m.ft_team2), COALESCE(m.et_team1, m.ft_team1), m.pen_team2, m.pen_team1)
) AS s(team_id, opponent_id, gf, ga, pen_for, pen_against);

-- Étape 2 : buts ordonnés dans le temps ---------------------------------------
CREATE TEMP TABLE ranked_goal ON COMMIT DROP AS
SELECT g.*, g.minute * 100 + COALESCE(g.added_time, 0) AS t,
       ROW_NUMBER() OVER (PARTITION BY g.match_id, g.team_id ORDER BY g.minute, COALESCE(g.added_time, 0), g.id) AS team_rank,
       ROW_NUMBER() OVER (PARTITION BY g.match_id ORDER BY g.minute DESC, COALESCE(g.added_time, 0) DESC, g.id DESC) AS reverse_rank
FROM goal g;

-- Étape 3 : buts décisifs ------------------------------------------------------
-- But de la victoire : celui qui dépasse le total final de l'adversaire.
-- Égalisation : dernier but d'un match nul. Un but contre son camp ne compte pas pour son auteur.
CREATE TEMP TABLE decisive_goal ON COMMIT DROP AS
SELECT rg.id AS goal_id, rg.player_id,
       CASE WHEN tr.result = 'W' THEN 'WIN' ELSE 'EQUALIZER' END AS kind
FROM ranked_goal rg
JOIN team_result tr ON tr.match_id = rg.match_id AND tr.team_id = rg.team_id
WHERE NOT rg.is_own_goal
  AND ((tr.result = 'W' AND rg.team_rank = tr.ga + 1)
    OR (tr.result = 'D' AND rg.reverse_rank = 1));

-- Étape 4 : temps de présence de chaque joueur entré en jeu -------------------
-- Entrée : coup d'envoi (titulaire) ou remplacement. Sortie : remplacement ou expulsion.
CREATE TEMP TABLE appearance ON COMMIT DROP AS
WITH entry AS (
    SELECT l.match_id, l.player_id, l.team_id, -1 AS on_t, 0 AS on_minute
    FROM match_lineup l WHERE l.is_starter
    UNION ALL
    SELECT s.match_id, s.player_in_id, s.team_id, s.minute * 100 + COALESCE(s.added_time, 0), s.minute
    FROM substitution s
), leave AS (
    SELECT match_id, player_out_id AS player_id, minute * 100 + COALESCE(added_time, 0) AS t, minute
    FROM substitution
    UNION ALL
    SELECT match_id, player_id, minute * 100 + COALESCE(added_time, 0), minute
    FROM booking WHERE card IN ('R', 'Y/R')
)
SELECT e.match_id, e.player_id, e.team_id, tr.opponent_id, tr.stage, tr.result, tr.won_shootout, tr.ga,
       e.on_t,
       COALESCE(x.t, tr.end_minute * 100 + 99) AS off_t,
       -- borné à la durée du match : la source note "121" les changements avant les tirs au but
       GREATEST(0, LEAST(COALESCE(x.minute, tr.end_minute), tr.end_minute) - e.on_minute) AS minutes_played
FROM entry e
JOIN team_result tr ON tr.match_id = e.match_id AND tr.team_id = e.team_id
LEFT JOIN LATERAL (
    SELECT l.t, l.minute FROM leave l
    WHERE l.match_id = e.match_id AND l.player_id = e.player_id AND l.t >= e.on_t
    ORDER BY l.t LIMIT 1
) x ON TRUE;

-- Étape 5 : statistiques par joueur et par match --------------------------------
CREATE TEMP TABLE player_stats ON COMMIT DROP AS
SELECT a.*,
       (SELECT COUNT(*) FROM goal g WHERE g.match_id = a.match_id AND g.player_id = a.player_id AND NOT g.is_own_goal) AS goals,
       (SELECT COUNT(*) FROM goal g WHERE g.match_id = a.match_id AND g.player_id = a.player_id AND NOT g.is_own_goal AND g.is_penalty) AS penalty_goals,
       (SELECT COUNT(*) FROM goal g WHERE g.match_id = a.match_id AND g.player_id = a.player_id AND g.is_own_goal) AS own_goals,
       (SELECT COUNT(*) FROM decisive_goal d JOIN goal g ON g.id = d.goal_id
         WHERE g.match_id = a.match_id AND d.player_id = a.player_id AND d.kind = 'WIN') AS winning_goals,
       (SELECT COUNT(*) FROM decisive_goal d JOIN goal g ON g.id = d.goal_id
         WHERE g.match_id = a.match_id AND d.player_id = a.player_id AND d.kind = 'EQUALIZER') AS equalizer_goals,
       (SELECT COUNT(*) FROM ranked_goal rg WHERE rg.match_id = a.match_id AND rg.team_id = a.team_id
         AND rg.t > a.on_t AND rg.t <= a.off_t) AS goals_for_on_pitch,
       (SELECT COUNT(*) FROM ranked_goal rg WHERE rg.match_id = a.match_id AND rg.team_id = a.opponent_id
         AND rg.t > a.on_t AND rg.t <= a.off_t) AS goals_against_on_pitch,
       (SELECT COUNT(*) FROM booking b WHERE b.match_id = a.match_id AND b.player_id = a.player_id AND b.card = 'Y') AS yellow_cards,
       (SELECT COUNT(*) FROM booking b WHERE b.match_id = a.match_id AND b.player_id = a.player_id AND b.card = 'Y/R') AS second_yellows,
       (SELECT COUNT(*) FROM booking b WHERE b.match_id = a.match_id AND b.player_id = a.player_id AND b.card = 'R') AS reds,
       (SELECT COUNT(*) FROM penalty_kick k WHERE k.match_id = a.match_id AND k.player_id = a.player_id AND k.scored) AS shootout_scored,
       (SELECT COUNT(*) FROM penalty_kick k WHERE k.match_id = a.match_id AND k.player_id = a.player_id AND NOT k.scored) AS shootout_missed
FROM appearance a;

-- Étape 6 : notes joueurs --------------------------------------------------------
DELETE FROM player_match_rating;

INSERT INTO player_match_rating (match_id, player_id, team_id, rating, minutes_played, goals, penalty_goals,
                                 own_goals, decisive_goals, goals_for_on_pitch, goals_against_on_pitch,
                                 yellow_cards, sent_off, shootout_scored, shootout_missed, algorithm_version)
SELECT s.match_id, s.player_id, s.team_id,
       ROUND(LEAST(10, GREATEST(0, p.base + c.coef * (
           LEAST(1.0, s.minutes_played::numeric / p.full_weight_minutes) * (
               CASE s.result WHEN 'W' THEN p.player_win WHEN 'L' THEN p.player_loss ELSE 0 END
             + CASE WHEN s.won_shootout THEN p.player_shootout_win ELSE 0 END
             + LEAST(p.on_pitch_cap, GREATEST(-p.on_pitch_cap,
                   s.goals_for_on_pitch * p.goal_for_on_pitch + s.goals_against_on_pitch * p.goal_against_on_pitch))
             + CASE WHEN s.ga = 0 AND s.minutes_played >= p.clean_sheet_min_minutes THEN p.clean_sheet ELSE 0 END)
         + (s.goals - s.penalty_goals) * p.goal + s.penalty_goals * p.penalty_goal + s.own_goals * p.own_goal
         + s.winning_goals * p.winning_goal + s.equalizer_goals * p.equalizer_goal
         + s.yellow_cards * p.yellow + s.second_yellows * p.second_yellow + s.reds * p.red
         + s.shootout_scored * p.shootout_scored + s.shootout_missed * p.shootout_missed
       ))), 1),
       s.minutes_played, s.goals, s.penalty_goals, s.own_goals, s.winning_goals + s.equalizer_goals,
       s.goals_for_on_pitch, s.goals_against_on_pitch, s.yellow_cards, (s.second_yellows + s.reds) > 0,
       s.shootout_scored, s.shootout_missed, p.version
FROM player_stats s
JOIN stage_coef c ON c.stage = s.stage
CROSS JOIN p;

-- Étape 7 : notes équipes --------------------------------------------------------
-- Fair-play (barème FIFA) : jaune -1, deuxième jaune -3 au total, rouge direct -4
DELETE FROM team_match_rating;

INSERT INTO team_match_rating (match_id, team_id, rating, result, points, goals_for, goals_against,
                               won_shootout, fair_play_points, algorithm_version)
SELECT tr.match_id, tr.team_id,
       ROUND(LEAST(10, GREATEST(0, p.base + c.coef * (
           CASE tr.result WHEN 'W' THEN p.team_win WHEN 'L' THEN p.team_loss ELSE 0 END
         + CASE WHEN tr.won_shootout THEN p.team_shootout WHEN NOT tr.won_shootout THEN -p.team_shootout ELSE 0 END
         + LEAST(p.team_goal_diff_cap, GREATEST(-p.team_goal_diff_cap, (tr.gf - tr.ga) * p.team_goal_diff))
         + LEAST(p.team_goal_cap, tr.gf * p.team_goal)
         + CASE WHEN tr.ga = 0 THEN p.team_clean_sheet ELSE 0 END
         + fp.points * p.fair_play_point
       ))), 1),
       tr.result,
       CASE tr.result WHEN 'W' THEN 3 WHEN 'D' THEN 1 ELSE 0 END,
       tr.gf, tr.ga, tr.won_shootout, fp.points, p.version
FROM team_result tr
JOIN stage_coef c ON c.stage = tr.stage
CROSS JOIN p
CROSS JOIN LATERAL (
    SELECT COALESCE(SUM(CASE b.card WHEN 'Y' THEN -1 WHEN 'Y/R' THEN -2 WHEN 'R' THEN -4 END), 0) AS points
    FROM booking b WHERE b.match_id = tr.match_id AND b.team_id = tr.team_id
) fp;

--COMMIT;


-- =============================================================================
-- PARTIE 4 : STATISTIQUES DÉTAILLÉES PAR MATCH (absentes de worldcup-full.json)
-- =============================================================================
-- Fautes, coups francs, duels, corners, hors-jeu, distance et homme du match ne
-- figurent pas dans le format openfootball. Ces tables les accueillent, à remplir
-- par un import complémentaire (feuilles de stats officielles, fournisseur de données).
--
-- Convention : NULL = donnée inconnue, 0 = valeur mesurée nulle. Les vues de la
-- partie 5 gardent cette distinction : un total reste NULL tant qu'aucun match du
-- joueur ou de l'équipe n'a la donnée.
--
-- Coups francs : "obtenus" = fautes subies, "concédés" = fautes sifflées contre
-- le joueur ou l'équipe et sanctionnées d'un coup franc.

CREATE TABLE IF NOT EXISTS player_match_stats (
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
CREATE UNIQUE INDEX IF NOT EXISTS uq_player_stats_motm
    ON player_match_stats(match_id) WHERE is_man_of_the_match;
CREATE INDEX IF NOT EXISTS idx_player_stats_player ON player_match_stats(player_id);

-- Totaux d'équipe tels que publiés. Facultatifs : à défaut, la partie 5 additionne
-- les lignes joueurs du match (les corners et hors-jeu sont souvent publiés
-- seulement au niveau de l'équipe).
CREATE TABLE IF NOT EXISTS team_match_stats (
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


-- =============================================================================
-- PARTIE 5 : VUES DU TOURNOI (onglets "Joueurs" et "Équipes" de l'IHM)
-- =============================================================================
-- Une ligne par joueur (ou équipe) et par compétition : filtrer sur competition_id.
-- Matchs joués, minutes, buts et notes viennent des tables de notes : relancer
-- la partie 3 après chaque import pour que les vues soient à jour.
--
-- Note globale : moyenne des notes de match (rating). Pour les joueurs, s'y ajoute
-- une moyenne pondérée par les minutes jouées (rating_weighted), pour qu'une
-- entrée de 5 minutes ne pèse pas autant qu'un match complet.
-- Cartons : yellow_cards = jaunes ('Y'), red_cards = rouges directs et deuxièmes
-- jaunes ('R' et 'Y/R'). Le premier jaune d'une double sanction reste un jaune.
-- detailed_stats_matches = nombre de matchs couverts par les tables de la partie 4.

-- Onglet "Joueurs" -------------------------------------------------------------
-- Seuls les joueurs entrés en jeu sont listés ; un carton reçu sur le banc n'est
-- pas compté, comme dans la notation.
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

-- Requêtes de l'IHM ------------------------------------------------------------
--   Onglet Joueurs : SELECT * FROM v_player_tournament_stats
--                    WHERE competition_id = :competition
--                    ORDER BY rating_weighted DESC NULLS LAST, minutes_played DESC;
--   Onglet Équipes : SELECT * FROM v_team_tournament_stats
--                    WHERE competition_id = :competition
--                    ORDER BY rating DESC, points DESC, goals_for - goals_against DESC;


-- =============================================================================
-- PARTIE 6 : STATISTIQUES DÉTAILLÉES FICTIVES (en attendant une vraie source)
-- =============================================================================
-- ATTENTION : données INVENTÉES. Cette partie efface puis remplit player_match_stats
-- et team_match_stats. Ne plus l'exécuter une fois des stats réelles importées.
--
-- Générées à partir des matchs réels, de façon déterministe : un même pseudo-aléa
-- (md5 de la graine + identifiants) donne les mêmes valeurs à chaque exécution.
-- Changer la graine dans pg_temp.rnd pour obtenir un autre tirage.
--
-- Règles de cohérence :
--   - Tout est proportionnel au temps de jeu (une prolongation = x 4/3), 0 pour un
--     joueur entré à la 120e. Les totaux d'équipe sont la somme de leurs joueurs.
--   - Domination (0,32 à 0,68) tirée de l'écart de buts + aléa ; elle fait monter
--     corners, hors-jeu et duels gagnés, et baisser les fautes.
--   - Duels : les duels gagnés d'une équipe sont les duels perdus de l'adversaire.
--   - Fautes : au moins une par carton reçu (jaune, deuxième jaune, rouge).
--   - Coups francs concédés = fautes + hors-jeu (un hors-jeu donne un coup franc
--     indirect) ; coups francs obtenus par l'équipe = coups francs concédés par
--     l'adversaire. Pour un joueur, "obtenus" = fautes subies, réparties entre les
--     joueurs à partir des fautes de l'adversaire. Les penaltys ne sont pas isolés.
--   - Gardien (premier titulaire de la feuille de match, retrouvé sans la position
--     qui n'est pas stockée) : environ 5 km, pas de corner ni de hors-jeu, très
--     peu de fautes et de duels.
--   - Hors-jeu surtout pour les buteurs du tournoi, corners concentrés sur quelques
--     tireurs par équipe, distance propre à chaque joueur (9,6 à 11,6 km / 90 min).
--   - Homme du match : meilleure note du match (puis buts, victoire, minutes).
-- Les totaux entiers d'une équipe sont répartis entre ses joueurs au prorata d'un
-- poids, au plus fort reste : la somme des joueurs tombe juste.

-- Tirage pseudo-aléatoire dans [0, 1), stable pour des arguments donnés
CREATE FUNCTION pg_temp.rnd(VARIADIC parts text[]) RETURNS double precision
LANGUAGE sql IMMUTABLE AS $$
    SELECT ('x' || substr(md5('fictif-v1|' || array_to_string(parts, '|')), 1, 8))::bit(32)::bigint / 4294967296.0
$$;

-- Gardien de chaque équipe à chaque match ---------------------------------------
-- Le n°1 est le premier joueur créé de l'équipe (premier titulaire de son premier
-- match). S'il ne joue pas, c'est un titulaire jamais aligné en même temps que lui.
CREATE TEMP TABLE fs_goalkeeper ON COMMIT DROP AS
WITH first_gk AS (
    SELECT team_id, MIN(id) AS player_id FROM player GROUP BY team_id
), starter AS (
    SELECT match_id, team_id, player_id FROM match_lineup WHERE is_starter
)
SELECT s.match_id, s.team_id, s.player_id
FROM starter s JOIN first_gk g ON g.player_id = s.player_id
UNION ALL
SELECT DISTINCT ON (s.match_id, s.team_id) s.match_id, s.team_id, s.player_id
FROM starter s
JOIN first_gk g ON g.team_id = s.team_id
WHERE NOT EXISTS (SELECT 1 FROM starter x WHERE x.match_id = s.match_id AND x.player_id = g.player_id)
  AND NOT EXISTS (SELECT 1 FROM starter a JOIN starter b ON b.match_id = a.match_id AND b.player_id = g.player_id
                  WHERE a.player_id = s.player_id)
ORDER BY 1, 2, 3;

-- Équipes : domination et objectifs par match -------------------------------------
-- Les tirages "de match" sont faits du point de vue de team1, puis inversés pour
-- team2 : les deux équipes d'un match restent complémentaires.
CREATE TEMP TABLE fs_team ON COMMIT DROP AS
WITH side AS (
    SELECT r.match_id, r.team_id, o.team_id AS opponent_id,
           CASE WHEN m.team1_id = r.team_id THEN 1 ELSE -1 END AS sign,
           r.goals_for - r.goals_against AS diff,
           CASE WHEN m.et_team1 IS NULL THEN 1.0 ELSE 4.0 / 3 END AS f
    FROM team_match_rating r
    JOIN match m ON m.id = r.match_id
    JOIN team_match_rating o ON o.match_id = r.match_id AND o.team_id <> r.team_id
), shares AS (
    SELECT s.*,
           LEAST(0.68, GREATEST(0.32, 0.5 + 0.035 * s.diff
                 + s.sign * 0.15 * (pg_temp.rnd(s.match_id::text, 'dom') - 0.5))) AS dom,
           -- part des duels gagnés par team1, puis nombre total de duels du match
           LEAST(0.6, GREATEST(0.4, 0.5 + 0.03 * s.diff * s.sign
                 + 0.08 * (pg_temp.rnd(s.match_id::text, 'duels') - 0.5))) AS duel_share1,
           ROUND(s.f * (170 + 50 * pg_temp.rnd(s.match_id::text, 'duels_n'))) AS duels_total
    FROM side s
)
SELECT match_id, team_id, opponent_id, f, dom,
       ROUND(f * (8 + 7 * pg_temp.rnd(match_id::text, team_id::text, 'fouls') + 10 * (0.5 - dom)))::int AS fouls,
       ROUND(f * (1 + 9 * dom * pg_temp.rnd(match_id::text, team_id::text, 'corners')
                  + 1.5 * pg_temp.rnd(match_id::text, team_id::text, 'corners2')))::int AS corners,
       ROUND(f * 6 * dom * pg_temp.rnd(match_id::text, team_id::text, 'offsides'))::int AS offsides,
       CASE WHEN sign = 1 THEN ROUND(duels_total * duel_share1)
            ELSE duels_total - ROUND(duels_total * duel_share1) END::int AS duels_won,
       CASE WHEN sign = 1 THEN duels_total - ROUND(duels_total * duel_share1)
            ELSE ROUND(duels_total * duel_share1) END::int AS duels_lost
FROM shares;

-- Joueurs entrés en jeu -------------------------------------------------------------
CREATE TEMP TABLE fs_player ON COMMIT DROP AS
SELECT r.match_id, r.team_id, r.player_id, r.minutes_played AS minutes,
       gk.player_id IS NOT NULL AS is_gk,
       tg.goals AS tournament_goals,
       (SELECT COUNT(*) FROM booking b WHERE b.match_id = r.match_id AND b.player_id = r.player_id) AS cards
FROM player_match_rating r
LEFT JOIN fs_goalkeeper gk ON gk.match_id = r.match_id AND gk.player_id = r.player_id
JOIN (SELECT player_id, SUM(goals) AS goals FROM player_match_rating GROUP BY player_id) tg
  ON tg.player_id = r.player_id;

-- Poids de chaque joueur pour chaque stat (profil stable + temps de jeu)
CREATE TEMP TABLE fs_weight ON COMMIT DROP AS
SELECT fp.match_id, fp.team_id, fp.player_id, w.stat, fp.minutes * w.weight AS weight
FROM fs_player fp
CROSS JOIN LATERAL (VALUES
    ('fouls',      CASE WHEN fp.is_gk THEN 0.08 ELSE 0.6 + 0.8 * pg_temp.rnd(fp.player_id::text, 'rugged') END),
    ('duels_won',  CASE WHEN fp.is_gk THEN 0.1 ELSE (0.6 + 0.8 * pg_temp.rnd(fp.player_id::text, 'duelist'))
                                                     * (0.8 + 0.4 * pg_temp.rnd(fp.player_id::text, 'strength')) END),
    ('duels_lost', CASE WHEN fp.is_gk THEN 0.1 ELSE (0.6 + 0.8 * pg_temp.rnd(fp.player_id::text, 'duelist'))
                                                     * (1.2 - 0.4 * pg_temp.rnd(fp.player_id::text, 'strength')) END),
    ('corners',    CASE WHEN fp.is_gk THEN 0 ELSE power(pg_temp.rnd(fp.player_id::text, 'corner_taker'), 8) END),
    ('offsides',   CASE WHEN fp.is_gk THEN 0 ELSE 0.15 + LEAST(fp.tournament_goals, 6)
                                                   + 2 * power(pg_temp.rnd(fp.player_id::text, 'striker'), 4) END),
    ('fouls_won',  CASE WHEN fp.is_gk THEN 0.05 ELSE 0.5 + 0.8 * pg_temp.rnd(fp.player_id::text, 'dribbler')
                                                   + 0.2 * LEAST(fp.tournament_goals, 5) END)
) AS w(stat, weight);

-- Répartition des totaux d'équipe au plus fort reste
CREATE TEMP TABLE fs_alloc (match_id INT, team_id INT, player_id INT, stat TEXT, value INT) ON COMMIT DROP;

-- 1er passage : fautes, duels, corners, hors-jeu
INSERT INTO fs_alloc
WITH total AS (
    SELECT match_id, team_id, v.stat, v.total
    FROM fs_team
    CROSS JOIN LATERAL (VALUES ('fouls', fouls), ('duels_won', duels_won), ('duels_lost', duels_lost),
                               ('corners', corners), ('offsides', offsides)) AS v(stat, total)
), share AS (
    SELECT w.match_id, w.team_id, w.player_id, w.stat, t.total,
           COALESCE(t.total * w.weight / NULLIF(SUM(w.weight) OVER (PARTITION BY w.match_id, w.team_id, w.stat), 0), 0) AS share
    FROM fs_weight w JOIN total t ON t.match_id = w.match_id AND t.team_id = w.team_id AND t.stat = w.stat
), base AS (
    SELECT s.*, FLOOR(s.share)::int AS base,
           ROW_NUMBER() OVER (PARTITION BY s.match_id, s.team_id, s.stat ORDER BY s.share - FLOOR(s.share) DESC, s.player_id) AS rk
    FROM share s
)
SELECT match_id, team_id, player_id, stat,
       base + CASE WHEN share > 0 AND rk <= total - SUM(base) OVER (PARTITION BY match_id, team_id, stat) THEN 1 ELSE 0 END
FROM base;

-- Au moins une faute par carton
UPDATE fs_alloc a SET value = fp.cards
FROM fs_player fp
WHERE a.stat = 'fouls' AND fp.match_id = a.match_id AND fp.player_id = a.player_id AND a.value < fp.cards;

-- 2e passage : fautes subies = fautes réellement commises par l'adversaire
INSERT INTO fs_alloc
WITH total AS (
    SELECT t.match_id, t.team_id, 'fouls_won'::text AS stat, SUM(a.value)::int AS total
    FROM fs_team t
    JOIN fs_alloc a ON a.match_id = t.match_id AND a.team_id = t.opponent_id AND a.stat = 'fouls'
    GROUP BY t.match_id, t.team_id
), share AS (
    SELECT w.match_id, w.team_id, w.player_id, w.stat, t.total,
           COALESCE(t.total * w.weight / NULLIF(SUM(w.weight) OVER (PARTITION BY w.match_id, w.team_id), 0), 0) AS share
    FROM fs_weight w JOIN total t ON t.match_id = w.match_id AND t.team_id = w.team_id AND t.stat = w.stat
), base AS (
    SELECT s.*, FLOOR(s.share)::int AS base,
           ROW_NUMBER() OVER (PARTITION BY s.match_id, s.team_id ORDER BY s.share - FLOOR(s.share) DESC, s.player_id) AS rk
    FROM share s
)
SELECT match_id, team_id, player_id, stat,
       base + CASE WHEN share > 0 AND rk <= total - SUM(base) OVER (PARTITION BY match_id, team_id) THEN 1 ELSE 0 END
FROM base;

-- Écriture -----------------------------------------------------------------------
DELETE FROM player_match_stats;
DELETE FROM team_match_stats;

INSERT INTO player_match_stats (match_id, player_id, team_id, fouls_committed, free_kicks_won, free_kicks_conceded,
                                duels_won, duels_lost, corners, offsides, distance_km, is_man_of_the_match)
SELECT fp.match_id, fp.player_id, fp.team_id,
       a.fouls, a.fouls_won, a.fouls + a.offsides,
       a.duels_won, a.duels_lost, a.corners, a.offsides,
       ROUND((fp.minutes / 90.0 * CASE
           WHEN fp.is_gk THEN 4.6 + 0.8 * pg_temp.rnd(fp.player_id::text, 'engine')
           ELSE 9.6 + 2.0 * pg_temp.rnd(fp.player_id::text, 'engine')
                + 0.6 * (pg_temp.rnd(fp.match_id::text, fp.player_id::text, 'km') - 0.5)
       END)::numeric, 2),
       fp.player_id = motm.player_id
FROM fs_player fp
JOIN (
    SELECT match_id, player_id,
           SUM(value) FILTER (WHERE stat = 'fouls')      AS fouls,
           SUM(value) FILTER (WHERE stat = 'fouls_won')  AS fouls_won,
           SUM(value) FILTER (WHERE stat = 'duels_won')  AS duels_won,
           SUM(value) FILTER (WHERE stat = 'duels_lost') AS duels_lost,
           SUM(value) FILTER (WHERE stat = 'corners')    AS corners,
           SUM(value) FILTER (WHERE stat = 'offsides')   AS offsides
    FROM fs_alloc GROUP BY match_id, player_id
) a ON a.match_id = fp.match_id AND a.player_id = fp.player_id
JOIN (
    SELECT DISTINCT ON (r.match_id) r.match_id, r.player_id
    FROM player_match_rating r
    JOIN team_match_rating t ON t.match_id = r.match_id AND t.team_id = r.team_id
    ORDER BY r.match_id, r.rating DESC, r.goals DESC, t.result = 'W' DESC, r.minutes_played DESC, r.player_id
) motm ON motm.match_id = fp.match_id;

INSERT INTO team_match_stats (match_id, team_id, fouls_committed, free_kicks_won, free_kicks_conceded,
                              duels_won, duels_lost, corners, offsides, distance_km)
SELECT s.match_id, s.team_id, s.fouls, o.fouls + o.offsides, s.fouls + s.offsides,
       s.duels_won, s.duels_lost, s.corners, s.offsides, s.distance_km
FROM (
    SELECT match_id, team_id, SUM(fouls_committed) AS fouls, SUM(duels_won) AS duels_won, SUM(duels_lost) AS duels_lost,
           SUM(corners) AS corners, SUM(offsides) AS offsides, SUM(distance_km) AS distance_km
    FROM player_match_stats GROUP BY match_id, team_id
) s
JOIN fs_team t ON t.match_id = s.match_id AND t.team_id = s.team_id
JOIN (
    SELECT match_id, team_id, SUM(fouls_committed) AS fouls, SUM(offsides) AS offsides
    FROM player_match_stats GROUP BY match_id, team_id
) o ON o.match_id = s.match_id AND o.team_id = t.opponent_id;

COMMIT;
