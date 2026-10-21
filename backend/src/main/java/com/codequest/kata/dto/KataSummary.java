package com.codequest.kata.dto;

import com.codequest.domain.Difficulty;
import com.codequest.domain.Track;
import java.util.List;

/**
 * Vista resumida de un reto, para listados.
 *
 * @param id              identificador
 * @param track           pista
 * @param title           titulo
 * @param difficulty      dificultad
 * @param tags            etiquetas
 * @param xp              recompensa
 * @param estimatedMinutes estimacion de tiempo
 * @param status          estado de progreso de la persona
 * @param bestScore       mejor puntuacion (0..1), null si no se ha intentado
 * @param attempts        numero de intentos
 */
public record KataSummary(
		String id,
		Track track,
		String title,
		Difficulty difficulty,
		List<String> tags,
		int xp,
		int estimatedMinutes,
		String status,
		Double bestScore,
		int attempts) {
}
