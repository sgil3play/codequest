package com.codequest.kata.dto;

/**
 * Resultado de un unico caso de test.
 *
 * @param name    nombre del metodo de test
 * @param status  estado final
 * @param message motivo del fallo (mensaje de asercion), vacio si paso
 */
public record TestCaseResult(String name, TestStatus status, String message) {

	public enum TestStatus {
		PASSED,
		FAILED,
		SKIPPED,
		ABORTED
	}
}
