package com.emocio.analysefoot.player.exception;

/** Raised when a player name given to a comparison by name is blank. */
public class BlankComparisonNameException extends RuntimeException {

	public BlankComparisonNameException(String message) {
		super(message);
	}

}
