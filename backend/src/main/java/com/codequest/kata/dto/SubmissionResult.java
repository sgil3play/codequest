package com.codequest.kata.dto;

import java.util.List;

/**
 * Resultado de enviar una solucion a un reto.
 *
 * @param kataId        reto evaluado
 * @param status        veredicto global
 * @param cases         detalle por caso de test
 * @param passed        casos superados
 * @param total         casos ejecutados
 * @param score         proporcion superada (0..1)
 * @param compilerOutput salida del compilador cuando hay error de compilacion
 * @param programOutput salida del codigo del usuario durante la ejecucion
 * @param message       resumen legible del resultado
 * @param durationMs    tiempo total de compilacion mas ejecucion
 */
public record SubmissionResult(
		String kataId,
		Status status,
		List<TestCaseResult> cases,
		int passed,
		int total,
		double score,
		String compilerOutput,
		String programOutput,
		String message,
		long durationMs) {

	public enum Status {
		/** Todos los casos pasan. */
		PASSED,
		/** El codigo compila pero algun caso falla. */
		TESTS_FAILED,
		/** El codigo no compila. */
		COMPILATION_ERROR,
		/** Se supero el limite de tiempo. */
		TIMEOUT,
		/** Fallo no recuperable al ejecutar la suite. */
		RUNTIME_ERROR
	}
}
