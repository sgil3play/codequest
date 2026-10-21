package com.codequest.common;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduccion centralizada de excepciones a respuestas HTTP.
 *
 * <p>Objetivo: que el frontend solo tenga que entender un unico formato de error,
 * sin casos especiales por endpoint.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), null, request);
	}

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), null, request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
			HttpServletRequest request) {
		Map<String, Object> details = new LinkedHashMap<>();
		for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
			details.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
		}
		return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "La peticion no es valida", details, request);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "El cuerpo de la peticion no se puede interpretar",
				null, request);
	}

	/**
	 * Un parametro de query con un valor que no existe en el enum (por ejemplo
	 * {@code ?track=NO_EXISTE}) es un error de la peticion, no del servidor.
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
				"El valor del parametro '" + ex.getName() + "' no es valido", null, request);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
			HttpServletRequest request) {
		return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage(), null, request);
	}

	/** Ruta inexistente: el dispatcher de Spring no encuentra el recurso. */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "El recurso no existe", null, request);
	}

	/**
	 * Dos entregas simultaneas del mismo elemento pueden pedir la misma fila y
	 * una de las dos pierde la carrera de escritura. Con bloqueos optimistas se
	 * traduce a 409 en lugar de a un 500 generico.
	 */
	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "CONFLICT",
				"Hay otra peticion en curso sobre el mismo recurso. Intentalo de nuevo.", null, request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
		// Se registra el stack completo en servidor, pero al cliente solo se le
		// devuelve un mensaje generico: no filtramos detalles internos.
		log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
				"Se ha producido un error inesperado. Revisa los logs del servidor.", null, request);
	}

	private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
			Map<String, Object> details, HttpServletRequest request) {
		ApiError body = new ApiError(
				status.value(),
				code,
				message,
				details,
				Instant.now(),
				request.getRequestURI());
		return ResponseEntity.status(status).body(body);
	}
}
