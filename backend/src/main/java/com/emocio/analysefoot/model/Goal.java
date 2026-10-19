package com.emocio.analysefoot.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Match match;

    /** Équipe qui bénéficie du but. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Team team;

    /** Buteur : joueur adverse en cas de but contre son camp. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Player player;

    private Short minute;

    private Short addedTime;

    private boolean isPenalty;

    private boolean isOwnGoal;

    protected Goal() {
    }

    public Goal(Match match, Team team, Player player, Short minute, Short addedTime,
                boolean isPenalty, boolean isOwnGoal) {
        this.match = match;
        this.team = team;
        this.player = player;
        this.minute = minute;
        this.addedTime = addedTime;
        this.isPenalty = isPenalty;
        this.isOwnGoal = isOwnGoal;
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

    public Player getPlayer() {
        return player;
    }

    public Short getMinute() {
        return minute;
    }

    public Short getAddedTime() {
        return addedTime;
    }

    public boolean isPenalty() {
        return isPenalty;
    }

    public boolean isOwnGoal() {
        return isOwnGoal;
    }
}
