package com.emocio.analysefoot.model;

import java.io.Serializable;

public record PenaltyKickId(Integer match, Short kickOrder) implements Serializable {
}
