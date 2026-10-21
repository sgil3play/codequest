package com.codequest.progress.dto;

import java.util.List;

/**
 * Resumen de progreso que alimenta el dashboard.
 *
 * @param username        identificador del perfil
 * @param totalXp         experiencia acumulada
 * @param level           nivel actual
 * @param levelProgress   progreso dentro del nivel (0..1)
 * @param xpToNextLevel   XP que faltan para el siguiente nivel
 * @param streakDays      racha de dias consecutivos con actividad
 * @param completedItems  elementos completados
 * @param totalItems      elementos disponibles en el catalogo
 * @param byTrack         desglose por pista
 * @param dailyQuestId    reto recomendado para hoy
 * @param lastActivity    ultima actividad registrada
 */
public record ProgressSummary(
		String username,
		int totalXp,
		int level,
		double levelProgress,
		int xpToNextLevel,
		int streakDays,
		int completedItems,
		int totalItems,
		List<TrackProgress> byTrack,
		String dailyQuestId,
		java.time.LocalDate lastActivity) {

	/**
	 * @param track        pista
	 * @param completed    elementos completados de la pista
	 * @param total        elementos totales de la pista
	 */
	public record TrackProgress(String track, int completed, int total) {
	}
}
