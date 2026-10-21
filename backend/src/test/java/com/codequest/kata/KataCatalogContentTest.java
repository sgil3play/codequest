package com.codequest.kata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codequest.common.config.CatalogReader;
import com.codequest.common.config.RunnerProperties;
import com.codequest.kata.dto.SubmissionResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test de contenido: comprueba que TODO el catalogo esta bien formado.
 *
 * <p>El catalogo vive en un fichero YAML con codigo Java incrustado, asi que es
 * facil que una entrada quede rota (un test que no compila, una clase con mal
 * nombre). Este test compila cada kata del catalogo: si alguien anade un reto
 * con un test roto, la build falla antes de que llegue a produccion.
 */
class KataCatalogContentTest {

	private KataRunner runner;
	private KataCatalog catalog;

	@BeforeEach
	void setUp() {
		runner = new KataRunner(new RunnerProperties(5, 30, 262_144, null, null, null, null));
		runner.resolveJunitJar();
		catalog = new KataCatalog(new CatalogReader());
	}

	@Test
	@DisplayName("El catalogo no esta vacio y todas las entradas tienen identificador unico")
	void catalogoValido() {
		List<KataDefinition> katas = catalog.findAll();
		assertTrue(katas.size() >= 8, "El catalogo deberia tener al menos 8 retos, tiene " + katas.size());

		long distintos = katas.stream().map(KataDefinition::id).distinct().count();
		assertEquals(katas.size(), distintos, "Hay identificadores duplicados en el catalogo");

		for (KataDefinition kata : katas) {
			assertTrue(kata.prompt() != null && !kata.prompt().isBlank(), kata.id() + ": falta el enunciado");
			assertTrue(kata.starterCode() != null && !kata.starterCode().isBlank(), kata.id() + ": falta el codigo inicial");
			assertTrue(kata.testSource() != null && !kata.testSource().isBlank(), kata.id() + ": falta el codigo de test");
			assertTrue(kata.explanation() != null && !kata.explanation().isBlank(), kata.id() + ": falta la explicacion");
			assertTrue(kata.testClass().equals(kata.solutionClass() + "Test"),
					kata.id() + ": la clase de test deberia llamarse " + kata.solutionClass() + "Test");
			assertTrue(kata.solutionClass().matches("[A-Z][A-Za-z0-9]*"),
					kata.id() + ": nombre de clase no valido: " + kata.solutionClass());
		}
	}

	@Test
	@DisplayName("El codigo inicial y el codigo de test de cada kata compilan")
	void todoCompila() {
		for (KataDefinition kata : catalog.findAll()) {
			// Se ejecuta el codigo inicial: si hay un error de compilacion, el
			// estado devuelto lo dira y el test fallara con el detalle.
			SubmissionResult result = runner.run(kata, kata.starterCode());
			assertTrue(
					result.status() != SubmissionResult.Status.COMPILATION_ERROR,
					() -> "El kata '" + kata.id() + "' no compila:\n" + result.compilerOutput());
		}
	}
}
