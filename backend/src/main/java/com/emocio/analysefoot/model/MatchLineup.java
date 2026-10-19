package com.emocio.analysefoot.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.ManyToOne;

@Entity
@IdClass(MatchLineupId.class)
public class MatchLineup {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    private Match match;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    private Player player;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Team team;

    private boolean isStarter;

    private boolean isCaptain;

    protected MatchLineup() {
    }

    public MatchLineup(Match match, Player player, Team team, boolean isStarter, boolean isCaptain) {
        this.match = match;
        this.player = player;
        this.team = team;
        this.isStarter = isStarter;
        this.isCaptain = isCaptain;
    }

    public Match getMatch() {
        return match;
    }

    public Player getPlayer() {
        return player;
    }

    public Team getTeam() {
        return team;
    }

    public boolean isStarter() {
        return isStarter;
    }

    public boolean isCaptain() {
        return isCaptain;
    }
}
