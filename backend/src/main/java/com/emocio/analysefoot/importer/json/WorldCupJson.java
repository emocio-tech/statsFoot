package com.emocio.analysefoot.importer.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Structure du fichier worldcup-full.json (format openfootball). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorldCupJson(String name, List<MatchJson> matches) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatchJson(
            String round,
            String date,
            String time,
            String team1,
            String team2,
            ScoreJson score,
            List<GoalJson> goals1,
            List<GoalJson> goals2,
            String ground,
            Integer attendance,
            List<LineupJson> lineup,
            List<List<BookingJson>> bookings,
            List<RefereeJson> referees,
            List<PenaltyJson> penalties) {

        public MatchJson {
            goals1 = goals1 == null ? List.of() : goals1;
            goals2 = goals2 == null ? List.of() : goals2;
            lineup = lineup == null ? List.of() : lineup;
            bookings = bookings == null ? List.of() : bookings;
            referees = referees == null ? List.of() : referees;
            penalties = penalties == null ? List.of() : penalties;
        }
    }

    /** Chaque score est une paire [team1, team2]. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScoreJson(List<Integer> ht, List<Integer> ft, List<Integer> et, List<Integer> p) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GoalJson(String name, String minute, boolean penalty, boolean owngoal) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LineupJson(List<LineupPlayerJson> starter, List<LineupPlayerJson> bench, List<SubJson> subs) {

        public LineupJson {
            starter = starter == null ? List.of() : starter;
            bench = bench == null ? List.of() : bench;
            subs = subs == null ? List.of() : subs;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LineupPlayerJson(String name, boolean captain) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubJson(String on, String off, String minute) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BookingJson(String type, String name, String minute) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RefereeJson(String name, String country) {
    }

    /** Tir au but : note = "missed" s'il est raté. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PenaltyJson(String name, String note) {
    }
}
