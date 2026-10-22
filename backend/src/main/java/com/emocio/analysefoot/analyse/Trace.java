package com.emocio.analysefoot.analyse;

import com.emocio.analysefoot.dto.TraceStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Trace des échanges d'une requête (appli, LLM, tool rank, MCP, SQL) : chaque étape est journalisée (logger
 * {@code analyse.trace}, niveau INFO) et rassemblée pour l'interface. Une trace par thread, de {@link #begin()} à
 * {@link #end()}. Désactivable par {@code analyse.trace.enabled=false} : la question saisie est alors de nouveau
 * absente des journaux.
 */
@Component
public final class Trace {

    private static final Logger log = LoggerFactory.getLogger("analyse.trace");
    private static final int MAX_DETAIL = 600;
    private static final ThreadLocal<Run> CURRENT = new ThreadLocal<>();
    private static volatile boolean enabled = true;

    private record Run(long start, List<TraceStep> steps) {
    }

    Trace(@Value("${analyse.trace.enabled:true}") boolean enabled) {
        Trace.enabled = enabled;
    }

    /** Ouvre une trace ; faux si une trace est déjà ouverte sur ce thread (appel imbriqué) ou si elle est désactivée. */
    public static boolean begin() {
        if (!enabled || CURRENT.get() != null) {
            return false;
        }
        CURRENT.set(new Run(System.nanoTime(), new ArrayList<>()));
        return true;
    }

    public static void end() {
        CURRENT.remove();
    }

    /** Étapes enregistrées jusqu'ici (vide sans trace ouverte). */
    public static List<TraceStep> steps() {
        Run run = CURRENT.get();
        return run == null ? List.of() : List.copyOf(run.steps());
    }

    /** Appel sortant (aller). */
    public static void out(String actor, String label, String detail) {
        add(actor, "OUT", label, detail);
    }

    /** Retour. */
    public static void in(String actor, String label, String detail) {
        add(actor, "IN", label, detail);
    }

    private static void add(String actor, String direction, String label, String detail) {
        Run run = CURRENT.get();
        if (run == null) {
            return;
        }
        String shown = detail == null ? "" : detail.length() > MAX_DETAIL ? detail.substring(0, MAX_DETAIL) + "…" : detail;
        long ms = (System.nanoTime() - run.start()) / 1_000_000;
        run.steps().add(new TraceStep(actor, direction, label, shown, ms));
        log.info("[{}] {} {} | {} (+{} ms)", actor, "OUT".equals(direction) ? "-->" : "<--", label, shown, ms);
    }
}
