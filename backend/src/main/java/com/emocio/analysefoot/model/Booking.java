package com.emocio.analysefoot.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Player player;

    private Card card;

    private Short minute;

    private Short addedTime;

    protected Booking() {
    }

    public Booking(Match match, Team team, Player player, Card card, Short minute, Short addedTime) {
        this.match = match;
        this.team = team;
        this.player = player;
        this.card = card;
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

    public Player getPlayer() {
        return player;
    }

    public Card getCard() {
        return card;
    }

    public Short getMinute() {
        return minute;
    }

    public Short getAddedTime() {
        return addedTime;
    }
}
