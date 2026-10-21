package com.codequest.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codequest.common.config.CatalogReader;
import com.codequest.design.DesignCatalog;
import com.codequest.design.DesignDefinition;
import com.codequest.kata.KataCatalog;
import com.codequest.quiz.QuizCatalog;
import com.codequest.quiz.QuizDefinition;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test de contenido de los catalogos.
 *
 * <p>Los catalogos son ficheros YAML versionados: un error tipografico (una
 * clave mal escrita, un indice de opcion fuera de rango, un enunciado vacio)
 * no lo detecta el compilador, pero rompe la aplicacion al arrancar o deja
 * un reto inutilizable. Este test es la red de seguridad: si alguien anade
 * contenido roto, la build falla aqui y no en produccion.
 */
class CatalogContentTest {

	private static final CatalogReader READER = new CatalogReader();

	@Test
	@DisplayName("El catalogo de retos carga y es coherente")
	void catalogoDeKatas() {
		KataCatalog catalog = new KataCatalog(READER);
		List<com.codequest.kata.KataDefinition> katas = catalog.findAll();

		assertFalse(katas.isEmpty(), "El catalogo de retos esta vacio");
		assertEquals(katas.size(), distintos(katas.stream().map(com.codequest.kata.KataDefinition::id).toList()),
				"Hay identificadores de reto duplicados");

		for (com.codequest.kata.KataDefinition kata : katas) {
			String ctx = kata.id();
			assertFalse(blank(kata.title()), ctx + ": titulo vacio");
			assertFalse(blank(kata.prompt()), ctx + ": enunciado vacio");
			assertFalse(blank(kata.starterCode()), ctx + ": codigo inicial vacio");
			assertFalse(blank(kata.testSource()), ctx + ": codigo de test vacio");
			assertFalse(blank(kata.explanation()), ctx + ": explicacion vacia");
			assertTrue(kata.xp() > 0, ctx + ": XP debe ser positivo");
			assertTrue(kata.timeLimitSeconds() > 0, ctx + ": limite de tiempo debe ser positivo");
			assertEquals(kata.solutionClass() + "Test", kata.testClass(),
					ctx + ": la clase de test debe llamarse " + kata.solutionClass() + "Test");
		}
	}

	@Test
	@DisplayName("El banco de preguntas carga y cada pregunta es respondible")
	void catalogoDePreguntas() {
		QuizCatalog catalog = new QuizCatalog(READER);
		List<QuizDefinition> quizzes = catalog.findAll();

		assertFalse(quizzes.isEmpty(), "El banco de preguntas esta vacio");
		assertEquals(quizzes.size(), distintos(quizzes.stream().map(QuizDefinition::id).toList()),
				"Hay identificadores de pregunta duplicados");

		for (QuizDefinition quiz : quizzes) {
			String ctx = quiz.id();
			assertFalse(blank(quiz.topic()), ctx + ": tema vacio");
			assertFalse(blank(quiz.question()), ctx + ": enunciado vacio");
			assertFalse(blank(quiz.explanation()), ctx + ": explicacion vacia");
			assertTrue(quiz.options().size() >= 2, ctx + ": hacen falta al menos dos opciones");
			assertTrue(quiz.correctIndex() >= 0 && quiz.correctIndex() < quiz.options().size(),
					ctx + ": el indice de la opcion correcta esta fuera de rango");
			assertTrue(quiz.xp() > 0, ctx + ": XP debe ser positivo");
			// Todas las opciones deben ser distintas: si dos coinciden, la
			// pregunta tendria dos respuestas correctas.
			assertEquals(quiz.options().size(),
					distintos(quiz.options()),
					ctx + ": hay opciones repetidas");
		}
	}

	@Test
	@DisplayName("El catalogo de diseno carga y cada ejercicio esta completo")
	void catalogoDeDiseno() {
		DesignCatalog catalog = new DesignCatalog(READER);
		List<DesignDefinition> exercises = catalog.findAll();

		assertFalse(exercises.isEmpty(), "El catalogo de diseno esta vacio");
		assertEquals(exercises.size(), distintos(exercises.stream().map(DesignDefinition::id).toList()),
				"Hay identificadores de ejercicio duplicados");

		for (DesignDefinition design : exercises) {
			String ctx = design.id();
			assertFalse(blank(design.title()), ctx + ": titulo vacio");
			assertFalse(blank(design.question()), ctx + ": enunciado vacio");
			assertFalse(blank(design.scenario()), ctx + ": contexto vacio");
			assertFalse(blank(design.referenceAnswer()), ctx + ": respuesta de referencia vacia");
			assertFalse(design.requirements().isEmpty(), ctx + ": sin requisitos");
			assertFalse(design.questions().isEmpty(), ctx + ": sin preguntas que responder");
			assertTrue(design.xp() > 0, ctx + ": XP debe ser positivo");
		}
	}

	private static long distintos(List<String> values) {
		return values.stream().collect(Collectors.toMap(Function.identity(), v -> 1, (a, b) -> a)).size();
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
