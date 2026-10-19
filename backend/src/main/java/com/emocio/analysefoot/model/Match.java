package com.emocio.analysefoot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Competition competition;

    @Enumerated(EnumType.STRING)
    private Stage stage;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 1)
    private String groupName;

    private OffsetDateTime kickoffAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Stadium stadium;

    private Integer attendance;

    @ManyToOne(fetch = FetchType.LAZY)
    private Referee referee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Team team1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Team team2;

    private Short htTeam1;
    private Short htTeam2;
    private Short ftTeam1;
    private Short ftTeam2;
    private Short etTeam1;
    private Short etTeam2;
    private Short penTeam1;
    private Short penTeam2;

    protected Match() {
    }

    public Match(Competition competition, Stage stage, String groupName, OffsetDateTime kickoffAt,
                 Stadium stadium, Integer attendance, Referee referee, Team team1, Team team2) {
        this.competition = competition;
        this.stage = stage;
        this.groupName = groupName;
        this.kickoffAt = kickoffAt;
        this.stadium = stadium;
        this.attendance = attendance;
        this.referee = referee;
        this.team1 = team1;
        this.team2 = team2;
    }

    public void setHalfTimeScore(Short team1Goals, Short team2Goals) {
        this.htTeam1 = team1Goals;
        this.htTeam2 = team2Goals;
    }

    public void setFullTimeScore(Short team1Goals, Short team2Goals) {
        this.ftTeam1 = team1Goals;
        this.ftTeam2 = team2Goals;
    }

    public void setExtraTimeScore(Short team1Goals, Short team2Goals) {
        this.etTeam1 = team1Goals;
        this.etTeam2 = team2Goals;
    }

    public void setPenaltyScore(Short team1Goals, Short team2Goals) {
        this.penTeam1 = team1Goals;
        this.penTeam2 = team2Goals;
    }

    public Integer getId() {
        return id;
    }

    public Competition getCompetition() {
        return competition;
    }

    public Stage getStage() {
        return stage;
    }

    public String getGroupName() {
        return groupName;
    }

    public OffsetDateTime getKickoffAt() {
        return kickoffAt;
    }

    public Stadium getStadium() {
        return stadium;
    }

    public Integer getAttendance() {
        return attendance;
    }

    public Referee getReferee() {
        return referee;
    }

    public Team getTeam1() {
        return team1;
    }

    public Team getTeam2() {
        return team2;
    }

    public Short getHtTeam1() {
        return htTeam1;
    }

    public Short getHtTeam2() {
        return htTeam2;
    }

    public Short getFtTeam1() {
        return ftTeam1;
    }

    public Short getFtTeam2() {
        return ftTeam2;
    }

    public Short getEtTeam1() {
        return etTeam1;
    }

    public Short getEtTeam2() {
        return etTeam2;
    }

    public Short getPenTeam1() {
        return penTeam1;
    }

    public Short getPenTeam2() {
        return penTeam2;
    }
}
