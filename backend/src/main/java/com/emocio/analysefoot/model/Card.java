package com.emocio.analysefoot.model;

import java.util.Arrays;

public enum Card {
    YELLOW("Y"),
    RED("R"),
    SECOND_YELLOW("Y/R");

    private final String code;

    Card(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static Card fromCode(String code) {
        return Arrays.stream(values())
                .filter(card -> card.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Carton inconnu : " + code));
    }
}
