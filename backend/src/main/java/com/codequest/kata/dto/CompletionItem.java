package com.codequest.kata.dto;

/**
 * Un miembro que se puede completar tras un punto.
 *
 * @param label      texto que se muestra y que se inserta (por ejemplo {@code toString()})
 * @param kind       tipo de miembro: {@code method}, {@code field}, {@code constant} o {@code type}
 * @param detail     firma legible, para distinguir sobrecargas (por ejemplo {@code String toString()})
 * @param insertText texto exacto que se inserta en el editor
 * @param isStatic   si el miembro es estatico
 * @param inherited  si el miembro viene de una superclase y no de la propia clase
 */
public record CompletionItem(
		String label,
		String kind,
		String detail,
		String insertText,
		boolean isStatic,
		boolean inherited) {
}
