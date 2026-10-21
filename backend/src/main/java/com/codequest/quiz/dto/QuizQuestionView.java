package com.codequest.quiz.dto;

import com.codequest.domain.Difficulty;
import java.util.List;

/**
 * Pregunta sin la respuesta correcta. Es lo que ve la persona antes de
 * responder: el indice correcto nunca viaja al cliente antes de tiempo.
 *
 * @param id         identificador
 * @param topic      tema
 * @param question   enunciado
 * @param options    opciones
 * @param difficulty dificultad
 * @param tags       etiquetas
 * @param xp         recompensa
 */
public record QuizQuestionView(
		String id,
		String topic,
		String question,
		List<String> options,
		Difficulty difficulty,
		List<String> tags,
		int xp) {
}
