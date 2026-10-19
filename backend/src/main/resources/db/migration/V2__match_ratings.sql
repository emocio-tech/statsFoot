-- Notes de 0 à 10 par match, calculées par le skill notation-matchs

CREATE TABLE player_match_rating (
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

CREATE TABLE team_match_rating (
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

CREATE INDEX idx_player_rating_player ON player_match_rating(player_id);
CREATE INDEX idx_team_rating_team     ON team_match_rating(team_id);
