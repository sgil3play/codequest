package com.codequest.kata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Envio de una solucion.
 *
 * @param code    codigo Java completo de la clase
 * @param user    perfil de la persona (opcional; por defecto "sergio")
 */
public record SubmitRequest(
		@NotBlank(message = "El codigo no puede estar vacio")
		@Size(max = 100_000, message = "El codigo es demasiado largo")
		String code,
		String user) {

	public String resolvedUser() {
		return (user == null || user.isBlank()) ? "sergio" : user.trim();
	}
}
