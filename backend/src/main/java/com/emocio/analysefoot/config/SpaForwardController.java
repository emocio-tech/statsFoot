package com.emocio.analysefoot.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Renvoie les routes Angular (sans extension, hors /api) vers index.html
 * pour que le rafraîchissement d'une page du front ne donne pas un 404.
 */
@Controller
public class SpaForwardController {

    @GetMapping({"/{path:^(?!api|actuator)[^\\.]*}", "/{path:^(?!api|actuator)[^\\.]*}/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
