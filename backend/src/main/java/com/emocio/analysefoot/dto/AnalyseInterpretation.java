package com.emocio.analysefoot.dto;

/** Comment une question libre a été comprise : {@code source} KEYWORDS ou ASSISTANT, libellé de la question retenue. */
public record AnalyseInterpretation(String source, String understoodAs) {
}
