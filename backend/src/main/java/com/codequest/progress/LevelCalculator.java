package com.codequest.progress;

/**
 * Progresion de niveles a partir de la experiencia acumulada.
 *
 * <p>Curva cuadratica: el nivel N exige {@code 100 * N * (N-1) / 2} puntos.
 * Asi el nivel 2 cuesta 100 XP, el 3 cuesta 300, el 4 cuesta 600...
 * Se elige cuadratica (y no lineal) para que los primeros niveles se alcancen
 * rapido y los posteriores exijan constancia.
 */
public final class LevelCalculator {

	private static final int XP_STEP = 100;

	private LevelCalculator() {
	}

	/** XP minimo necesario para alcanzar el nivel indicado. */
	public static int xpForLevel(int level) {
		if (level <= 1) {
			return 0;
		}
		return XP_STEP * level * (level - 1) / 2;
	}

	/** Nivel correspondiente a una cantidad de XP. */
	public static int levelFor(int totalXp) {
		int level = 1;
		while (xpForLevel(level + 1) <= totalXp) {
			level++;
		}
		return level;
	}

	/**
	 * Progreso dentro del nivel actual, entre 0 y 1.
	 * Sirve para la barra de progreso de la UI.
	 */
	public static double progressWithinLevel(int totalXp) {
		int level = levelFor(totalXp);
		int floor = xpForLevel(level);
		int ceiling = xpForLevel(level + 1);
		if (ceiling == floor) {
			return 1;
		}
		return (double) (totalXp - floor) / (ceiling - floor);
	}
}
