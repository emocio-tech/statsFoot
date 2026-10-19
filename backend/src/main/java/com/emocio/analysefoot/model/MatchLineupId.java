package com.emocio.analysefoot.model;

import java.io.Serializable;

public record MatchLineupId(Integer match, Integer player) implements Serializable {
}
