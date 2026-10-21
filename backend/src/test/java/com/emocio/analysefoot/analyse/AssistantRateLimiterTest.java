package com.emocio.analysefoot.analyse;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantRateLimiterTest {

    /** Horloge réglable. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-21T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void thirtyCallsPerMinuteAtMost() {
        MutableClock clock = new MutableClock();
        AssistantRateLimiter limiter = new AssistantRateLimiter(30, clock);

        for (int i = 0; i < 30; i++) {
            assertThat(limiter.tryAcquire()).as("appel %d", i + 1).isTrue();
        }
        assertThat(limiter.tryAcquire()).isFalse();

        clock.now = clock.now.plus(Duration.ofSeconds(59));
        assertThat(limiter.tryAcquire()).isFalse();
        clock.now = clock.now.plus(Duration.ofSeconds(1));
        assertThat(limiter.tryAcquire()).isTrue();
    }
}
