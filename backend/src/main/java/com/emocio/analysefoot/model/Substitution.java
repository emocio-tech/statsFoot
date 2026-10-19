package com.emocio.analysefoot.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class Substitution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Player playerIn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Player playerOut;

    private Short minute;

    private Short addedTime;

    protected Substitution() {
    }

    public Substitution(Match match, Team team, Player playerIn, Player playerOut, Short minute, Short addedTime) {
        this.match = match;
        this.team = team;
        this.playerIn = playerIn;
        this.playerOut = playerOut;
        this.minute = minute;
        this.addedTime = addedTime;
    }

    public Integer getId() {
        return id;
    }

    public Match getMatch() {
        return match;
    }

    public Team getTeam() {
        return team;
    }

    public Player getPlayerIn() {
        return playerIn;
    }

    public Player getPlayerOut() {
        return playerOut;
    }

    public Short getMinute() {
        return minute;
    }

    public Short getAddedTime() {
        return addedTime;
    }
}
