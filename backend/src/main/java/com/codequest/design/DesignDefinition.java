package com.codequest.design;

import com.codequest.domain.Difficulty;
import com.codequest.domain.Track;
import java.util.List;

/**
 * Ejercicio de diseno de sistemas.
 *
 * <p>No se evalua con tests: se resuelve en texto libre y la persona se
 * autoevalua contra una respuesta de referencia. El objetivo es practicar el
 * guion de una pregunta de entrevista ("como lo disenyarias, que trade-offs...").
 *
 * @param id              identificador estable
 * @param title           titulo
 * @param question        enunciado del ejercicio, en Markdown
 * @param scenario        contexto de negocio
 * @param requirements    requisitos funcionales y no funcionales
 * @param questions       preguntas que la persona debe responder
 * @param hints           pistas que se pueden ir desbloqueando
 * @param referenceAnswer respuesta de referencia y puntos a defender
 * @param difficulty      dificultad
 * @param tags            etiquetas
 * @param xp              recompensa
 * @param estimatedMinutes estimacion de tiempo
 */
public record DesignDefinition(
		String id,
		String title,
		String question,
		String scenario,
		List<String> requirements,
		List<String> questions,
		List<String> hints,
		String referenceAnswer,
		Difficulty difficulty,
		Track track,
		List<String> tags,
		int xp,
		int estimatedMinutes) {

	public DesignDefinition {
		if (requirements == null) {
			requirements = List.of();
		}
		if (questions == null) {
			questions = List.of();
		}
		if (hints == null) {
			hints = List.of();
		}
		if (tags == null) {
			tags = List.of();
		}
	}
}
