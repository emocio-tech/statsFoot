package com.emocio.analysefoot.importer;

import com.emocio.analysefoot.model.Stage;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorldCupParsersTest {

    @Test
    void parsesRegularAndAddedTimeMinutes() {
        assertThat(WorldCupParsers.parseMinute("67")).isEqualTo(new WorldCupParsers.Minute((short) 67, null));
        assertThat(WorldCupParsers.parseMinute("90+12")).isEqualTo(new WorldCupParsers.Minute((short) 90, (short) 12));
        assertThatThrownBy(() -> WorldCupParsers.parseMinute("abc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesRounds() {
        assertThat(WorldCupParsers.parseRound("First Stage, Group L"))
                .isEqualTo(new WorldCupParsers.Round(Stage.GROUP, "L"));
        assertThat(WorldCupParsers.parseRound("Round of 32"))
                .isEqualTo(new WorldCupParsers.Round(Stage.ROUND_OF_32, null));
        assertThat(WorldCupParsers.parseRound("Bronze final"))
                .isEqualTo(new WorldCupParsers.Round(Stage.THIRD_PLACE, null));
        assertThatThrownBy(() -> WorldCupParsers.parseRound("Playoff")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesKickoffWithUtcOffset() {
        assertThat(WorldCupParsers.parseKickoff("2026-06-11", "13:00 UTC-6"))
                .isEqualTo(OffsetDateTime.of(2026, 6, 11, 13, 0, 0, 0, ZoneOffset.ofHours(-6)));
        assertThat(WorldCupParsers.parseKickoff("2026-06-11", "9:30 UTC+5:30"))
                .isEqualTo(OffsetDateTime.of(2026, 6, 11, 9, 30, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)));
        assertThat(WorldCupParsers.parseKickoff("2026-06-11", "20:00 UTC-3:30"))
                .isEqualTo(OffsetDateTime.of(2026, 6, 11, 20, 0, 0, 0, ZoneOffset.ofHoursMinutes(-3, -30)));
        assertThatThrownBy(() -> WorldCupParsers.parseKickoff("2026-06-11", "13h00"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesEveryKnockoutRound() {
        assertThat(WorldCupParsers.parseRound("Round of 16").stage()).isEqualTo(Stage.ROUND_OF_16);
        assertThat(WorldCupParsers.parseRound("Quarter-final").stage()).isEqualTo(Stage.QUARTER_FINAL);
        assertThat(WorldCupParsers.parseRound("Semi-final").stage()).isEqualTo(Stage.SEMI_FINAL);
        assertThat(WorldCupParsers.parseRound("Final")).isEqualTo(new WorldCupParsers.Round(Stage.FINAL, null));
    }

    @Test
    void splitsGroundIntoStadiumAndCity() {
        assertThat(WorldCupParsers.parseGround("BC Place Vancouver, Vancouver"))
                .isEqualTo(new WorldCupParsers.Ground("BC Place Vancouver", "Vancouver"));
        assertThat(WorldCupParsers.parseGround("Stadium, Part, City"))
                .isEqualTo(new WorldCupParsers.Ground("Stadium, Part", "City"));
        assertThat(WorldCupParsers.parseGround("Wembley"))
                .isEqualTo(new WorldCupParsers.Ground("Wembley", "Wembley"));
    }
}
