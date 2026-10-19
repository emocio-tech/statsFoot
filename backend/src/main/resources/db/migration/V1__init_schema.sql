-- Schéma initial : données de compétition issues de worldcup-full.json

-- Référentiels ---------------------------------------------------------
CREATE TABLE competition (
    id    SERIAL PRIMARY KEY,
    name  VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE team (
    id    SERIAL PRIMARY KEY,
    name  VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE stadium (
    id    SERIAL PRIMARY KEY,
    name  VARCHAR(150) NOT NULL,
    city  VARCHAR(100) NOT NULL,
    UNIQUE (name, city)
);

CREATE TABLE referee (
    id            SERIAL PRIMARY KEY,
    name          VARCHAR(150) NOT NULL UNIQUE,
    country_code  CHAR(3)      NOT NULL
);

CREATE TABLE player (
    id       SERIAL PRIMARY KEY,
    name     VARCHAR(150) NOT NULL,
    team_id  INT NOT NULL REFERENCES team(id),
    UNIQUE (name, team_id)
);

-- Match ----------------------------------------------------------------
CREATE TABLE match (
    id              SERIAL PRIMARY KEY,
    competition_id  INT NOT NULL REFERENCES competition(id),
    stage           VARCHAR(20) NOT NULL CHECK (stage IN
                      ('GROUP', 'ROUND_OF_32', 'ROUND_OF_16', 'QUARTER_FINAL',
                       'SEMI_FINAL', 'THIRD_PLACE', 'FINAL')),
    group_name      CHAR(1),
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
    et_team1        SMALLINT,  -- prolongation, NULL sinon
    et_team2        SMALLINT,
    pen_team1       SMALLINT,  -- tirs au but, NULL sinon
    pen_team2       SMALLINT,
    CHECK (team1_id <> team2_id)
);

-- Feuille de match -----------------------------------------------------
CREATE TABLE match_lineup (
    match_id    INT NOT NULL REFERENCES match(id) ON DELETE CASCADE,
    player_id   INT NOT NULL REFERENCES player(id),
    team_id     INT NOT NULL REFERENCES team(id),
    is_starter  BOOLEAN NOT NULL,
    is_captain  BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (match_id, player_id)
);

CREATE TABLE substitution (
    id             SERIAL PRIMARY KEY,
    match_id       INT NOT NULL REFERENCES match(id) ON DELETE CASCADE,
    team_id        INT NOT NULL REFERENCES team(id),
    player_in_id   INT NOT NULL REFERENCES player(id),
    player_out_id  INT NOT NULL REFERENCES player(id),
    minute         SMALLINT NOT NULL,
    added_time     SMALLINT
);

CREATE TABLE goal (
    id           SERIAL PRIMARY KEY,
    match_id     INT NOT NULL REFERENCES match(id) ON DELETE CASCADE,
    team_id      INT NOT NULL REFERENCES team(id),    -- équipe qui bénéficie du but
    player_id    INT NOT NULL REFERENCES player(id),  -- buteur (adversaire si CSC)
    minute       SMALLINT NOT NULL,
    added_time   SMALLINT,
    is_penalty   BOOLEAN NOT NULL DEFAULT FALSE,
    is_own_goal  BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE booking (
    id          SERIAL PRIMARY KEY,
    match_id    INT NOT NULL REFERENCES match(id) ON DELETE CASCADE,
    team_id     INT NOT NULL REFERENCES team(id),
    player_id   INT NOT NULL REFERENCES player(id),
    card        VARCHAR(3) NOT NULL CHECK (card IN ('Y', 'R', 'Y/R')),
    minute      SMALLINT NOT NULL,
    added_time  SMALLINT
);

CREATE TABLE penalty_kick (
    match_id    INT NOT NULL REFERENCES match(id) ON DELETE CASCADE,
    kick_order  SMALLINT NOT NULL,
    team_id     INT NOT NULL REFERENCES team(id),
    player_id   INT NOT NULL REFERENCES player(id),
    scored      BOOLEAN NOT NULL,
    PRIMARY KEY (match_id, kick_order)
);

CREATE INDEX idx_match_stage    ON match(stage, group_name);
CREATE INDEX idx_goal_player    ON goal(player_id);
CREATE INDEX idx_booking_player ON booking(player_id);
CREATE INDEX idx_lineup_player  ON match_lineup(player_id);
