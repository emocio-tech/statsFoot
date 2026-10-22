package com.emocio.analysefoot.embedded;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applique les parties de données de donneesStats.sql (3 : notes, 6 : stats fictives) une fois les matchs importés.
 * Le schéma (parties 1, 2, 4, 5) est déjà créé par Flyway : on n'exécute jamais que les parties 3 et 6.
 */
@Component
@Profile("embedded")
@Order(100)
public class DonneesStatsRunner implements ApplicationRunner {

    private static final Pattern PARTIE_3 = Pattern.compile("(?ms)(^-- PARTIE 3 :.*?)(?=^-- PARTIE 4 :)");
    private static final Pattern PARTIE_6 = Pattern.compile("(?ms)(^-- PARTIE 6 :.*)");

    private final DataSource dataSource;

    public DonneesStatsRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String sql = new String(new ClassPathResource("db/migration/donneesStats.sql").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        String donnees = extract(PARTIE_3, sql) + "\n" + extract(PARTIE_6, sql);
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute(donnees);
        }
    }

    private static String extract(Pattern pattern, String sql) {
        Matcher m = pattern.matcher(sql);
        if (!m.find()) {
            throw new IllegalStateException("Partie introuvable dans donneesStats.sql : " + pattern);
        }
        return m.group(1);
    }
}
