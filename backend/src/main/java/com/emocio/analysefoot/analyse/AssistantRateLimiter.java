package com.emocio.analysefoot.analyse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/** Plafond partagé des appels à l'assistant : au plus {@code maxPerMinute} sur toute fenêtre glissante de 60 s. */
public class AssistantRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final int maxPerMinute;
    private final Clock clock;
    private final Deque<Instant> calls = new ArrayDeque<>();

    public AssistantRateLimiter(int maxPerMinute, Clock clock) {
        this.maxPerMinute = maxPerMinute;
        this.clock = clock;
    }

    /** Réserve un appel ; faux si le plafond est atteint. */
    public synchronized boolean tryAcquire() {
        Instant now = clock.instant();
        while (!calls.isEmpty() && !calls.peekFirst().isAfter(now.minus(WINDOW))) {
            calls.pollFirst();
        }
        if (calls.size() >= maxPerMinute) {
            return false;
        }
        calls.addLast(now);
        return true;
    }
}
