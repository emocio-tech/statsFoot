package com.emocio.analysefoot.embedded;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.io.IOException;

/**
 * Profil « embedded » : un PostgreSQL embarqué, lancé et arrêté avec l'application, remplace le service sur 5432.
 * Le schéma vient de Flyway, les données de worldcup-full.json et de donneesStats.sql (voir {@link DonneesStatsRunner}).
 */
@Configuration
@Profile("embedded")
public class EmbeddedPostgresConfig {

    @Bean(destroyMethod = "close")
    EmbeddedPostgres embeddedPostgres(@Value("${analyse-foot.embedded-postgres.port:5432}") int port) throws IOException {
        return EmbeddedPostgres.builder().setPort(port).start();
    }

    @Bean
    DataSource dataSource(EmbeddedPostgres postgres) {
        return postgres.getPostgresDatabase();
    }
}
