package com.emocio.analysefoot.importer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/** Lance l'import au démarrage quand analyse-foot.import.worldcup-file est renseigné. */
@Component
@ConditionalOnProperty("analyse-foot.import.worldcup-file")
public class WorldCupImportRunner implements ApplicationRunner {

    private final WorldCupImporter importer;
    private final Path file;

    public WorldCupImportRunner(WorldCupImporter importer,
                                @Value("${analyse-foot.import.worldcup-file}") Path file) {
        this.importer = importer;
        this.file = file;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        importer.importFile(file);
    }
}
