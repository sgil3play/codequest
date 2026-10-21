package com.codequest.kata.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Peticion de autocompletado.
 *
 * @param code   codigo Java completo tal y como esta en el editor
 * @param offset posicion del cursor, en numero de caracteres desde el inicio
 */
public record CompletionRequest(
		@NotBlank(message = "El codigo no puede estar vacio")
		@Size(max = 100_000, message = "El codigo es demasiado largo")
		String code,
		@Min(value = 0, message = "La posicion del cursor no puede ser negativa")
		@Max(value = 100_000, message = "La posicion del cursor es demasiado grande")
		int offset) {
}
