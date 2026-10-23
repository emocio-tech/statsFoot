package com.emocio.analysefoot;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Entités et repositories JPA des deux racines de packages. Séparée de l'application pour que les tests de tranche
 * web ({@code @WebMvcTest}), qui ignorent les {@code @Configuration}, ne cherchent pas d'{@code entityManagerFactory}.
 */
@Configuration(proxyBeanMethods = false)
@EntityScan({"com.emocio.analysefoot", "stats_foot.stats_foot"})
@EnableJpaRepositories({"com.emocio.analysefoot", "stats_foot.stats_foot"})
class JpaScanConfig {
}
