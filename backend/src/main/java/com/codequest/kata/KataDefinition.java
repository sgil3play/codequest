package com.codequest.kata;

import com.codequest.domain.Difficulty;
import com.codequest.domain.Track;
import java.util.List;

/**
 * Definicion de un reto de codigo. Se carga desde {@code data/katas.json}.
 *
 * @param id                 identificador estable, se usa en la URL
 * @param track              pista a la que pertenece
 * @param title              titulo corto
 * @param difficulty         dificultad
 * @param tags               etiquetas para filtrar
 * @param xp                 recompensa en puntos de experiencia
 * @param estimatedMinutes   estimacion de tiempo
 * @param prompt             enunciado en Markdown
 * @param functionSignature  firma del metodo que hay que implementar
 * @param starterCode        codigo inicial que ve la persona
 * @param solutionClass      nombre de la clase que implementa la persona
 * @param testClass          nombre de la clase de test (oculta hasta que se pide)
 * @param testSource         codigo fuente de la clase de test
 * @param explanation        solucion comentada, visible tras pasar los tests
 * @param timeLimitSeconds   limite de tiempo por caso de test
 */
public record KataDefinition(
		String id,
		Track track,
		String title,
		Difficulty difficulty,
		List<String> tags,
		int xp,
		int estimatedMinutes,
		String prompt,
		String functionSignature,
		String starterCode,
		String solutionClass,
		String testClass,
		String testSource,
		String explanation,
		int timeLimitSeconds) {

	public KataDefinition {
		if (tags == null) {
			tags = List.of();
		}
		if (timeLimitSeconds <= 0) {
			timeLimitSeconds = 5;
		}
	}
}
