-- =============================================================================
-- donneesStats.sql : synthèse du modèle de données et de la notation statsFoot
-- =============================================================================
-- Regroupe en un seul script :
--   Partie 1  schéma des compétitions  (V1__init_schema.sql)
--   Partie 2  tables de notes          (V2__match_ratings.sql)
--   Partie 3  calcul des notes 0 à 10  (compute_ratings.sql du skill notation-matchs)
--
-- Usage (psql) :
--   psql -U analysefoot -h localhost -d <base> -v ON_ERROR_STOP=1 -f donneesStats.sql
--
-- Les parties 1 et 2 créent les tables absentes. Elles ne modifient pas une table
-- existante. La partie 3 efface et recalcule toutes les notes à chaque exécution.
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

COMMIT;
