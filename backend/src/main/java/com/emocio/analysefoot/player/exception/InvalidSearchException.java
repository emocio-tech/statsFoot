package com.emocio.analysefoot.player.exception;

/** Raised when the search query is null or blank. */
public class InvalidSearchException extends RuntimeException {

	public InvalidSearchException(String message) {
		super(message);
	}

}
