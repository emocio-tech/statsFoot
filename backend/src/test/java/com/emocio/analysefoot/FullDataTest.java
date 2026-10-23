package com.emocio.analysefoot;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.emocio.analysefoot.AnalyseFootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Full application on an embedded PostgreSQL (random port) filled like the {@code embedded} profile does:
 * matches imported from {@code worldcup-full.json}, then ratings and detailed statistics from
 * {@code donneesStats.sql}. The result is compared with the reference snapshot {@code data/donneesStats.json}.
 * Paths are relative to the {@code backend} module.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(classes = AnalyseFootApplication.class, properties = {
		"analyse-foot.embedded-postgres.port=0",
		"analyse-foot.import.worldcup-file=file:../worldcup-full.json"
})
@ActiveProfiles("embedded")
public @interface FullDataTest {

}
