package com.emocio.analysefoot.player.exception;

/** Raised when a player name given to a comparison by name matches several players. */
public class AmbiguousPlayerNameException extends RuntimeException {

	public AmbiguousPlayerNameException(String message) {
		super(message);
	}

}
