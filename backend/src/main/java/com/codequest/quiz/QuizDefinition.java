package com.codequest.quiz;

import com.codequest.domain.Difficulty;
import com.codequest.domain.Track;
import java.util.List;

/**
 * Pregunta conceptual de opcion multiple.
 *
 * <p>Cubre el bloque de teoria que la oferta marca como imprescindible:
 * Spring, JPA/Hibernate, SQL/Oracle, microservicios, SOAP, TDD y patrones.
 *
 * @param id           identificador estable
 * @param topic        tema agrupa las preguntas en la UI
 * @param question     enunciado
 * @param options      opciones; el indice correcto es {@code correctIndex}
 * @param correctIndex indice de la opcion correcta
 * @param explanation  justificacion, visible tras responder
 * @param difficulty   dificultad
 * @param tags         etiquetas
 * @param xp           recompensa
 */
public record QuizDefinition(
		String id,
		String topic,
		String question,
		List<String> options,
		int correctIndex,
		String explanation,
		Difficulty difficulty,
		Track track,
		List<String> tags,
		int xp) {

	public QuizDefinition {
		if (tags == null) {
			tags = List.of();
		}
	}
}
