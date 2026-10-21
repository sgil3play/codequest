package com.codequest.kata.dto;

import com.codequest.domain.Difficulty;
import com.codequest.domain.Track;
import java.util.List;

/**
 * Vista completa de un reto.
 *
 * <p>El codigo de los tests y la solucion comentada NO se exponen aqui: se
 * desbloquean de forma explicita para no regalar la respuesta.
 *
 * @param id                 identificador
 * @param track              pista
 * @param title              titulo
 * @param difficulty         dificultad
 * @param tags               etiquetas
 * @param xp                 recompensa
 * @param estimatedMinutes   estimacion
 * @param prompt             enunciado
 * @param functionSignature  firma del metodo a implementar
 * @param starterCode        codigo inicial
 * @param solutionClass      nombre de la clase a implementar
 * @param timeLimitSeconds   limite de tiempo por caso
 * @param status             estado de progreso
 * @param bestScore          mejor puntuacion
 * @param attempts           intentos
 */
public record KataDetail(
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
		int timeLimitSeconds,
		String status,
		Double bestScore,
		int attempts) {
}
