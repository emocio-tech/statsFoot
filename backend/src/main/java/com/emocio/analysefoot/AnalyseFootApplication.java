package com.emocio.analysefoot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Deux racines de packages cohabitent : {@code com.emocio.analysefoot} (onglet Analyse, rapports, notes) et
 * {@code stats_foot.stats_foot} (recherche et comparaison de joueurs et d'équipes, chat), issu de la branche main.
 * Les entités et les repositories des deux racines sont déclarés dans {@link JpaScanConfig}.
 */
@SpringBootApplication(scanBasePackages = {"com.emocio.analysefoot", "stats_foot.stats_foot"})
public class AnalyseFootApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyseFootApplication.class, args);
    }
}
