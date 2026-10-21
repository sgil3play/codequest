package com.codequest.kata;

/**
 * Analisis lexico minimo de un fichero Java.
 *
 * <p>El autocompletado necesita saber tres cosas del texto que hay alrededor
 * del cursor: si el cursor esta dentro de codigo (y no de una cadena o de un
 * comentario), donde esta el punto que precede al cursor y donde acaba el
 * identificador que se esta escribiendo.
 *
 * <p>No es un parser de Java: solo distingue codigo de literales y comentarios,
 * que es lo que hace falta para no ofrecer miembros dentro de un
 * {@code "texto.a."} ni dentro de un {@code // comentario con un punto}.
 */
final class JavaSourceScanner {

	private final String source;
	private final boolean[] code;

	private JavaSourceScanner(String source, boolean[] code) {
		this.source = source;
		this.code = code;
	}

	/** Analiza el fuente y marca que posiciones son codigo. */
	static JavaSourceScanner of(String source) {
		int length = source.length();
		boolean[] code = new boolean[length + 1];
		int i = 0;
		while (i < length) {
			char current = source.charAt(i);

			// Comentario de linea: hasta el salto de linea, que si es codigo.
			if (current == '/' && i + 1 < length && source.charAt(i + 1) == '/') {
				while (i < length && source.charAt(i) != '\n') {
					code[i] = false;
					i++;
				}
				continue;
			}

			// Comentario de bloque.
			if (current == '/' && i + 1 < length && source.charAt(i + 1) == '*') {
				code[i] = false;
				code[i + 1] = false;
				i += 2;
				while (i < length) {
					if (source.charAt(i) == '*' && i + 1 < length && source.charAt(i + 1) == '/') {
						code[i] = false;
						code[i + 1] = false;
						i += 2;
						break;
					}
					code[i] = false;
					i++;
				}
				continue;
			}

			// Bloque de texto (""" ... """).
			if (current == '"' && source.startsWith("\"\"\"", i)) {
				int end = source.indexOf("\"\"\"", i + 3);
				int stop = end < 0 ? length : end + 3;
				while (i < stop) {
					code[i] = false;
					i++;
				}
				continue;
			}

			// Literal de cadena.
			if (current == '"') {
				code[i] = false;
				i++;
				while (i < length) {
					char c = source.charAt(i);
					if (c == '\\' && i + 1 < length) {
						code[i] = false;
						code[i + 1] = false;
						i += 2;
						continue;
					}
					code[i] = false;
					i++;
					if (c == '"') {
						break;
					}
				}
				continue;
			}

			// Literal de caracter.
			if (current == '\'') {
				code[i] = false;
				i++;
				while (i < length) {
					char c = source.charAt(i);
					if (c == '\\' && i + 1 < length) {
						code[i] = false;
						code[i + 1] = false;
						i += 2;
						continue;
					}
					code[i] = false;
					i++;
					if (c == '\'') {
						break;
					}
				}
				continue;
			}

			code[i] = true;
			i++;
		}
		code[length] = true;
		return new JavaSourceScanner(source, code);
	}

	/** Indica si la posicion dada esta en codigo y no en un literal o comentario. */
	boolean isCode(int position) {
		return position >= 0 && position < code.length && code[position];
	}

	/**
	 * Devuelve la posicion del punto que precede al cursor, o -1 si no hay
	 * ninguno (el cursor no esta detras de un selector de miembro).
	 */
	int dotBefore(int cursor) {
		int i = cursor;
		// Se salta el identificador que se esta escribiendo.
		while (i > 0 && isIdentifierPart(source.charAt(i - 1))) {
			i--;
		}
		// Y los espacios que lo preceden.
		while (i > 0 && Character.isWhitespace(source.charAt(i - 1))) {
			i--;
		}
		if (i == 0 || source.charAt(i - 1) != '.') {
			return -1;
		}
		int dot = i - 1;
		// "1." es un literal decimal, no una seleccion de miembro.
		if (dot > 0 && Character.isDigit(source.charAt(dot - 1))) {
			return -1;
		}
		return dot;
	}

	/** Posicion justo despues del identificador que empieza en el cursor. */
	int identifierEnd(int cursor) {
		int i = cursor;
		while (i < source.length() && isIdentifierPart(source.charAt(i))) {
			i++;
		}
		return i;
	}

	/** Indica si el texto puede ser el inicio de un identificador de Java. */
	boolean isIdentifierPrefix(String text) {
		for (int i = 0; i < text.length(); i++) {
			if (!isIdentifierPart(text.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	private static boolean isIdentifierPart(char c) {
		return Character.isLetterOrDigit(c) || c == '_' || c == '$';
	}
}
