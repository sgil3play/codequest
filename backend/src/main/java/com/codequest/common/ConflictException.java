package com.codequest.common;

/** Conflicto de estado. Se traduce a 409 con codigo de negocio CONFLICT. */
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}
}
