package com.codequest.progress.dto;

import com.codequest.domain.Track;
import com.codequest.progress.ItemProgress;
import java.time.Instant;

/**
 * Vista de lectura del progreso de un elemento.
 *
 * <p>La entidad {@link ItemProgress} no se expone directamente en la API: su
 * clave compuesta interna y sus getters duplicados (username e itemId aparecen
 * a la vez dentro de {@code id} y en la raiz) formarian parte del contrato
 * publico sin querer. El DTO fija que datos salen y en que forma.
 *
 * @param itemId        identificador del elemento del catalogo
 * @param track         pista a la que pertenece
 * @param status        estado del progreso
 * @param attempts      numero de intentos registrados
 * @param bestScore     mejor puntuacion obtenida (0..1)
 * @param xpEarned      XP cocido en este elemento
 * @param completedAt   momento del primer acierto, si lo hubo
 * @param lastAttemptAt momento del ultimo intento
 */
public record ItemProgressView(
		String itemId,
		Track track,
		ItemProgress.Status status,
		int attempts,
		double bestScore,
		int xpEarned,
		Instant completedAt,
		Instant lastAttemptAt) {

	public static ItemProgressView from(ItemProgress progress) {
		return new ItemProgressView(
				progress.getItemId(),
				progress.getTrack(),
				progress.getStatus(),
				progress.getAttempts(),
				progress.getBestScore(),
				progress.getXpEarned(),
				progress.getCompletedAt(),
				progress.getLastAttemptAt());
	}
}
