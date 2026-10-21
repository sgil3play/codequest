package com.codequest.common;

import java.time.Instant;
import java.util.Map;

/**
 * Formato unico de error de la API. Todo el frontend consume esta estructura,
 * asi que no hay dos convenciones conviviendo en el proyecto.
 *
 * @param statusCode codigo HTTP
 * @param code       codigo de negocio estable (no depende del idioma del mensaje)
 * @param message    mensaje legible para la persona desarrolladora
 * @param details    mapa opcional con el detalle por campo (validaciones)
 * @param timestamp  momento de generacion
 * @param path       ruta que produjo el error
 */
public record ApiError(
		int statusCode,
		String code,
		String message,
		Map<String, Object> details,
		Instant timestamp,
		String path) {
}
