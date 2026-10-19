package com.emocio.analysefoot.importer;

import com.emocio.analysefoot.model.Stage;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conversion des valeurs textuelles du format openfootball. */
final class WorldCupParsers {

    private static final Pattern MINUTE = Pattern.compile("(\\d+)(?:\\+(\\d+))?");
    private static final Pattern GROUP_ROUND = Pattern.compile(".*Group ([A-Z])");
    private static final Pattern KICKOFF_TIME = Pattern.compile("(\\d{1,2}):(\\d{2}) UTC([+-]\\d{1,2})(?::(\\d{2}))?");

    private WorldCupParsers() {
    }

    /** "90+5" : minute 90, temps additionnel 5. */
    record Minute(short minute, Short addedTime) {
    }

    /** Phase de la compétition et groupe éventuel. */
    record Round(Stage stage, String group) {
    }

    /** "Mexico City Stadium, Mexico City" : nom et ville. */
    record Ground(String name, String city) {
    }

    static Minute parseMinute(String value) {
        Matcher matcher = MINUTE.matcher(value.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Minute invalide : " + value);
        }
        Short addedTime = matcher.group(2) == null ? null : Short.valueOf(matcher.group(2));
        return new Minute(Short.parseShort(matcher.group(1)), addedTime);
    }

    static Round parseRound(String value) {
        Matcher group = GROUP_ROUND.matcher(value);
        if (group.matches()) {
            return new Round(Stage.GROUP, group.group(1));
        }
        Stage stage = switch (value) {
            case "Round of 32" -> Stage.ROUND_OF_32;
            case "Round of 16" -> Stage.ROUND_OF_16;
            case "Quarter-final" -> Stage.QUARTER_FINAL;
            case "Semi-final" -> Stage.SEMI_FINAL;
            case "Bronze final", "Match for third place" -> Stage.THIRD_PLACE;
            case "Final" -> Stage.FINAL;
            default -> throw new IllegalArgumentException("Phase inconnue : " + value);
        };
        return new Round(stage, null);
    }

    /** date "2026-06-11" et heure "13:00 UTC-6". */
    static OffsetDateTime parseKickoff(String date, String time) {
        Matcher matcher = KICKOFF_TIME.matcher(time.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Heure invalide : " + time);
        }
        LocalTime localTime = LocalTime.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        int hours = Integer.parseInt(matcher.group(3));
        int minutes = matcher.group(4) == null ? 0 : Integer.parseInt(matcher.group(4));
        ZoneOffset offset = ZoneOffset.ofHoursMinutes(hours, hours < 0 ? -minutes : minutes);
        return OffsetDateTime.of(LocalDate.parse(date), localTime, offset);
    }

    static Ground parseGround(String value) {
        int separator = value.lastIndexOf(", ");
        if (separator < 0) {
            return new Ground(value.trim(), value.trim());
        }
        return new Ground(value.substring(0, separator).trim(), value.substring(separator + 2).trim());
    }
}
