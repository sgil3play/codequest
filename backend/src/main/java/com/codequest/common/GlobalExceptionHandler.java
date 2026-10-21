package com.codequest.common;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
