package com.emocio.analysefoot.importer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lance l'import au démarrage quand analyse-foot.import.worldcup-file est renseigné (le profil « embedded » le renseigne
 * par défaut). Un chemin relatif est cherché depuis le répertoire courant puis depuis son parent : l'application se lance
 * aussi bien de la racine du projet que du module backend. Un préfixe {@code file:} est accepté.
 */
@Component
@Order(50)
@ConditionalOnProperty("analyse-foot.import.worldcup-file")
public class WorldCupImportRunner implements ApplicationRunner {

    private final WorldCupImporter importer;
    private final String file;

    public WorldCupImportRunner(WorldCupImporter importer,
                                @Value("${analyse-foot.import.worldcup-file}") String file) {
        this.importer = importer;
        this.file = file;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        importer.importFile(resolve(file));
    }

    static Path resolve(String configured) {
        String name = configured.startsWith("file:") ? configured.substring("file:".length()) : configured;
        Path path = Path.of(name);
        if (Files.exists(path) || path.isAbsolute()) {
            return path;
        }
        Path inParent = Path.of("..").resolve(path).normalize();
        return Files.exists(inParent) ? inParent : path;
    }
}
