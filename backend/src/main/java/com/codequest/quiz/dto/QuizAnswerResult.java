package com.codequest.quiz.dto;

/**
 * Respuesta a una pregunta conceptual.
 *
 * @param correct      si la opcion elegida era la correcta
 * @param correctIndex indice de la opcion correcta
 * @param explanation  justificacion
 * @param xpAwarded    XP concedidos
 */
public record QuizAnswerResult(boolean correct, int correctIndex, String explanation, int xpAwarded) {
}
