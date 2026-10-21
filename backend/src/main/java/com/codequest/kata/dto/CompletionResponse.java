package com.codequest.kata.dto;

import java.util.List;

/**
 * Respuesta del autocompletado.
 *
 * @param items       candidatos, ya filtrados por el prefijo que se estaba escribiendo
 * @param replaceStart posicion inicial del texto a sustituir (justo despues del punto)
 * @param replaceEnd   posicion final del texto a sustituir (fin del identificador en curso)
 */
public record CompletionResponse(
		List<CompletionItem> items,
		int replaceStart,
		int replaceEnd) {

	public CompletionResponse {
		items = items == null ? List.of() : List.copyOf(items);
	}
}
