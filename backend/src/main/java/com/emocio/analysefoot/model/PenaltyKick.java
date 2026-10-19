package com.emocio.analysefoot.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.ManyToOne;

/** Tir de la séance de tirs au but. */
@Entity
@IdClass(PenaltyKickId.class)
public class PenaltyKick {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    private Match match;

    @Id
    private Short kickOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Player player;

    private boolean scored;

    protected PenaltyKick() {
    }

    public PenaltyKick(Match match, Short kickOrder, Team team, Player player, boolean scored) {
        this.match = match;
        this.kickOrder = kickOrder;
        this.team = team;
        this.player = player;
        this.scored = scored;
    }

    public Match getMatch() {
        return match;
    }

    public Short getKickOrder() {
        return kickOrder;
    }

    public Team getTeam() {
        return team;
    }

    public Player getPlayer() {
        return player;
    }

    public boolean isScored() {
        return scored;
    }
}
