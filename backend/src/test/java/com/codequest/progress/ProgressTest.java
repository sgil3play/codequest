package com.codequest.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codequest.domain.Track;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LevelCalculatorTest {

	@Test
	@DisplayName("Sin XP se esta en el nivel 1")
	void nivelInicial() {
		assertEquals(1, LevelCalculator.levelFor(0));
		assertEquals(1, LevelCalculator.levelFor(99));
	}

	@Test
	@DisplayName("Los umbrales de nivel son los esperados")
	void umbrales() {
		assertEquals(0, LevelCalculator.xpForLevel(1));
		assertEquals(100, LevelCalculator.xpForLevel(2));
		assertEquals(300, LevelCalculator.xpForLevel(3));
		assertEquals(600, LevelCalculator.xpForLevel(4));
	}

	@Test
	@DisplayName("El nivel sube en el umbral exacto")
	void cambioDeNivel() {
		assertEquals(1, LevelCalculator.levelFor(99));
		assertEquals(2, LevelCalculator.levelFor(100));
		assertEquals(2, LevelCalculator.levelFor(299));
		assertEquals(3, LevelCalculator.levelFor(300));
	}

	@Test
	@DisplayName("El progreso dentro del nivel va de 0 a 1")
	void progresoDentroDelNivel() {
		assertEquals(0.0, LevelCalculator.progressWithinLevel(0), 0.001);
		assertEquals(0.5, LevelCalculator.progressWithinLevel(200), 0.001);
		assertEquals(0.0, LevelCalculator.progressWithinLevel(300), 0.001);
	}
}

class UserProgressTest {

	@Test
	@DisplayName("La racha continua cuando se repite actividad al dia siguiente")
	void rachaContinua() {
		UserProgress progress = new UserProgress("sergio");
		LocalDate hoy = LocalDate.of(2026, 10, 21);

		progress.registerActivity(hoy);
		assertEquals(1, progress.getStreakDays());

		progress.registerActivity(hoy.plusDays(1));
		assertEquals(2, progress.getStreakDays());

		progress.registerActivity(hoy.plusDays(2));
		assertEquals(3, progress.getStreakDays());
	}

	@Test
	@DisplayName("Volver a actuar el mismo dia no altera la racha")
	void actividadEnElMismoDia() {
		UserProgress progress = new UserProgress("sergio");
		LocalDate hoy = LocalDate.of(2026, 10, 21);

		progress.registerActivity(hoy);
		progress.registerActivity(hoy);
		assertEquals(1, progress.getStreakDays());
	}

	@Test
	@DisplayName("Un dia sin actividad reinicia la racha")
	void rachaRota() {
		UserProgress progress = new UserProgress("sergio");
		LocalDate hoy = LocalDate.of(2026, 10, 21);

		progress.registerActivity(hoy);
		progress.registerActivity(hoy.plusDays(3));
		assertEquals(1, progress.getStreakDays());
	}

	@Test
	@DisplayName("El nivel se recalcula al ganar XP")
	void nivelPorXp() {
		UserProgress progress = new UserProgress("sergio");
		progress.addXp(150);
		assertEquals(150, progress.getTotalXp());
		assertEquals(2, progress.getLevel());
		assertTrue(progress.getLevel() >= 1);
	}
}

class ItemProgressTest {

	@Test
	@DisplayName("Un intento fallido posterior no tira atras un reto ya superado")
	void elProgresoEsAcumulativo() {
		ItemProgress item = new ItemProgress("sergio", "java-optional-001", Track.JAVA_CORE);

		item.registerAttempt(1.0, 10, true);
		assertEquals(ItemProgress.Status.COMPLETED, item.getStatus());

		// Un intento posterior que falla no puede deshacer el completado.
		item.registerAttempt(0.0, 0, false);
		assertEquals(ItemProgress.Status.COMPLETED, item.getStatus());
		assertEquals(1.0, item.getBestScore());
		assertEquals(2, item.getAttempts());
	}

	@Test
	@DisplayName("La mejor puntuacion se conserva y el XP se cobra una sola vez")
	void laMejorPuntuacionSeMantiene() {
		ItemProgress item = new ItemProgress("sergio", "java-optional-001", Track.JAVA_CORE);

		item.registerAttempt(0.5, 0, false);
		assertEquals(ItemProgress.Status.IN_PROGRESS, item.getStatus());

		item.registerAttempt(0.8, 10, true);
		assertEquals(ItemProgress.Status.COMPLETED, item.getStatus());
		assertEquals(0.8, item.getBestScore());
		assertEquals(10, item.getXpEarned());

		item.registerAttempt(1.0, 10, true);
		assertEquals(10, item.getXpEarned());
	}

	@Test
	@DisplayName("La fecha de completado se fija en el primer intento superado")
	void laFechaDeCompletadoNoCambia() {
		ItemProgress item = new ItemProgress("sergio", "java-optional-001", Track.JAVA_CORE);

		item.registerAttempt(1.0, 10, true);
		var primerCompletado = item.getCompletedAt();

		item.registerAttempt(1.0, 10, true);
		assertEquals(primerCompletado, item.getCompletedAt());
	}
}
