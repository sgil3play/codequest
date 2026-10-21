package com.codequest.common;

/** Recurso inexistente. Se traduce a 404 con codigo de negocio NOT_FOUND. */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}
}
