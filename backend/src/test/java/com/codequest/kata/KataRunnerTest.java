package com.codequest.kata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codequest.common.config.RunnerProperties;
import com.codequest.domain.Difficulty;
import com.codequest.domain.Track;
import com.codequest.kata.dto.SubmissionResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests del motor de ejecucion.
 *
 * <p>Es la pieza mas critica del proyecto: si el runner no compila o no parsea
 * bien el protocolo, toda la aplicacion queda inutil. Estos tests cubren los
 * cuatro desenlaces posibles (compila y pasa, compila y falla, no compila, se
 * queda sin tiempo) y el caso limite del codigo hostil.
 */
class KataRunnerTest {

	private KataRunner runner;

	@BeforeEach
	void setUp() {
		runner = new KataRunner(new RunnerProperties(5, 30, 30, 4, 262_144, null, null, null, null));
		runner.resolveJunitJar();
	}

	private KataDefinition kata() {
		return new KataDefinition(
				"test-001",
				Track.JAVA_CORE,
				"Suma dos numeros",
				Difficulty.EASY,
				List.of(),
				10,
				5,
				"Suma dos numeros",
				"int sumar(int a, int b)",
				"""
						public class Solution {
						    public int sumar(int a, int b) {
						        throw new UnsupportedOperationException("TODO");
						    }
						}
						""",
				"Solution",
				"SolutionTest",
				"""
						import static org.junit.jupiter.api.Assertions.assertEquals;

						import org.junit.jupiter.api.Test;

						class SolutionTest {
						    @Test
						    void sumaCorrecta() {
						        assertEquals(3, new Solution().sumar(1, 2));
						    }
						}
						""",
				"Devuelve a + b",
				5);
	}

	@Test
	@DisplayName("Una solucion correcta pasa todos los casos")
	void solucionCorrecta() {
		SubmissionResult result = runner.run(kata(), """
				public class Solution {
				    public int sumar(int a, int b) {
				        return a + b;
				    }
				}
				""");

		assertEquals(SubmissionResult.Status.PASSED, result.status());
		assertEquals(1, result.passed());
		assertEquals(1, result.total());
		assertEquals(1.0, result.score());
		assertTrue(result.cases().get(0).message() == null || result.cases().get(0).message().isBlank());
	}

	@Test
	@DisplayName("Una solucion incorrecta compila pero falla los tests")
	void solucionIncorrecta() {
		SubmissionResult result = runner.run(kata(), """
				public class Solution {
				    public int sumar(int a, int b) {
				        return a - b;
				    }
				}
				""");

		assertEquals(SubmissionResult.Status.TESTS_FAILED, result.status());
		assertEquals(0, result.passed());
		assertEquals(1, result.total());
		assertNotNull(result.cases().get(0).message());
		assertTrue(result.cases().get(0).message().contains("expected"), result.cases().get(0).message());
	}

	@Test
	@DisplayName("Un error de compilacion se reporta con la salida del compilador")
	void errorDeCompilacion() {
		SubmissionResult result = runner.run(kata(), """
				public class Solution {
				    public int sumar(int a, int b) {
				        return a +
				    }
				}
				""");

		assertEquals(SubmissionResult.Status.COMPILATION_ERROR, result.status());
		assertNotNull(result.compilerOutput());
		assertFalse(result.compilerOutput().isBlank());
		assertTrue(result.compilerOutput().contains("error"), result.compilerOutput());
	}

	@Test
	@DisplayName("Un bucle infinito se corta por timeout")
	void timeout() {
		SubmissionResult result = runner.run(kata(), """
				public class Solution {
				    public int sumar(int a, int b) {
				        while (true) {
				            // bucle infinito
				        }
				    }
				}
				""");

		assertEquals(SubmissionResult.Status.TIMEOUT, result.status());
	}

	@Test
	@DisplayName("Una excepcion en tiempo de ejecucion se reporta como fallo del caso")
	void excepcionEnEjecucion() {
		SubmissionResult result = runner.run(kata(), """
				public class Solution {
				    public int sumar(int a, int b) {
				        throw new IllegalStateException("boom");
				    }
				}
				""");

		assertEquals(SubmissionResult.Status.TESTS_FAILED, result.status());
		assertTrue(result.cases().get(0).message().contains("boom"), result.cases().get(0).message());
	}

	@Test
	@DisplayName("Las llamadas a System.out del usuario no rompen el protocolo")
	void salidaDelUsuario() {
		SubmissionResult result = runner.run(kata(), """
				public class Solution {
				    public int sumar(int a, int b) {
				        System.out.println("|PASS|falso|");
				        System.err.println("mensaje de diagnostico");
				        return a + b;
				    }
				}
				""");

		assertEquals(SubmissionResult.Status.PASSED, result.status());
		assertEquals(1, result.passed());
		assertTrue(result.programOutput().contains("mensaje de diagnostico"), result.programOutput());
	}
}
